package transit

import (
	"context"
	"fmt"
	"net/url"
	"regexp"
	"strings"
	"time"
	"unicode"
)

// Journey is one way to get from A to B in full detail, for the directions
// screen: every leg with its line, colour and station codes, the walks with
// the station exit and platform where the map data names them, and the path
// of each leg for drawing on a map. (Option, above, is the slimmed-down
// version we give the LLM.)
type Journey struct {
	Minutes     int          `json:"minutes"`
	Transfers   int          `json:"transfers"`
	WalkMinutes int          `json:"walk_minutes"`
	Departs     string       `json:"departs"`
	Arrives     string       `json:"arrives"`
	Legs        []JourneyLeg `json:"legs"`
}

// JourneyLeg is one walk or ride.
type JourneyLeg struct {
	// Mode is walk, train, shinkansen, subway, tram, bus, ferry or other.
	Mode     string `json:"mode"`
	Line     string `json:"line,omitempty"`
	LineJa   string `json:"line_ja,omitempty"`   // "JR中央・総武緩行線", as on the signs
	LineCode string `json:"line_code,omitempty"` // "JB", as on the signs
	Color    string `json:"color,omitempty"`     // the line's colour, "#ffdd3a"
	Operator string `json:"operator,omitempty"`
	Towards  string `json:"towards,omitempty"`
	From     Stop   `json:"from"`
	To       Stop   `json:"to"`
	Departs  string `json:"departs"`
	Arrives  string `json:"arrives"`
	Minutes  int    `json:"minutes"`
	// Rides: how many stops until you get off.
	Stops int `json:"stops,omitempty"`
	// Walks: how far, the station exit used ("A1"), the platform the next
	// train leaves from ("1"), and the named streets or paths on the way.
	// Exit and platform are only set when the map data names them, so the
	// app never guesses.
	DistanceM int      `json:"distance_m,omitempty"`
	Exit      string   `json:"exit,omitempty"`
	Platform  string   `json:"platform,omitempty"`
	Via       []string `json:"via,omitempty"`
	// Path is the leg's route as [lat, lng] points, for the map.
	Path [][2]float64 `json:"path"`
}

// Stop is one end of a leg. Name is in English when the data has it
// ("Sendagaya"), NameJa is the Japanese on the station signs ("千駄ケ谷"),
// and Code is the station number on the signs ("JB12").
type Stop struct {
	Name   string  `json:"name"`
	NameJa string  `json:"name_ja,omitempty"`
	Code   string  `json:"code,omitempty"`
	Lat    float64 `json:"lat"`
	Lng    float64 `json:"lng"`
}

// maxPathPoints caps each leg's path. A long Shinkansen leg has thousands of
// points; a few hundred draw the same line on a phone.
const maxPathPoints = 300

// Directions finds up to three different ways from one point to another,
// leaving at departAt. fromName and toName label the two ends (Transitous
// calls them START and END).
func (c *Client) Directions(ctx context.Context, from, to LatLng, fromName, toName string, departAt time.Time) ([]Journey, error) {
	departAt = departAt.Truncate(10 * time.Minute)

	key := fmt.Sprintf("directions|%.4f,%.4f|%.4f,%.4f|%d|%s|%s", from.Lat, from.Lng, to.Lat, to.Lng, departAt.Unix(), fromName, toName)
	if v, ok := c.cached(key); ok {
		return v.([]Journey), nil
	}

	params := url.Values{
		"fromPlace": {fmt.Sprintf("%.5f,%.5f", from.Lat, from.Lng)},
		"toPlace":   {fmt.Sprintf("%.5f,%.5f", to.Lat, to.Lng)},
		"time":      {departAt.UTC().Format(time.RFC3339)},
	}

	type place struct {
		Name      string    `json:"name"`
		StopCode  string    `json:"stopCode"`
		Lat       float64   `json:"lat"`
		Lon       float64   `json:"lon"`
		Departure time.Time `json:"departure"`
		Arrival   time.Time `json:"arrival"`
	}
	var plan struct {
		Itineraries []struct {
			Duration  int       `json:"duration"`
			Transfers int       `json:"transfers"`
			StartTime time.Time `json:"startTime"`
			EndTime   time.Time `json:"endTime"`
			Legs      []struct {
				Mode              string  `json:"mode"`
				Duration          int     `json:"duration"`
				Distance          float64 `json:"distance"`
				RouteShortName    string  `json:"routeShortName"`
				RouteLongName     string  `json:"routeLongName"`
				RouteColor        string  `json:"routeColor"`
				AgencyName        string  `json:"agencyName"`
				Headsign          string  `json:"headsign"`
				From              place   `json:"from"`
				To                place   `json:"to"`
				IntermediateStops []place `json:"intermediateStops"`
				LegGeometry       struct {
					Points    string `json:"points"`
					Precision int    `json:"precision"`
				} `json:"legGeometry"`
				Steps []struct {
					StreetName string `json:"streetName"`
				} `json:"steps"`
			} `json:"legs"`
		} `json:"itineraries"`
	}

	if err := c.get(ctx, "/plan", params, &plan); err != nil {
		return nil, err
	}

	stop := func(p place) Stop {
		switch p.Name {
		case "START":
			return Stop{Name: fromName, Lat: p.Lat, Lng: p.Lon}
		case "END":
			return Stop{Name: toName, Lat: p.Lat, Lng: p.Lon}
		}
		en, ja := splitName(p.Name)
		// A name only in Japanese: look for the English in the station
		// snapshot or romanize.
		if ja == "" && hasJapaneseRune(en) {
			cleanJa := CleanStationName(en)
			if c.Stations != nil {
				if english := c.Stations.English(cleanJa, p.Lat, p.Lon); english != "" {
					en, ja = english, cleanJa
				} else {
					en, ja = RomanizeStation(cleanJa), cleanJa
				}
			} else {
				en, ja = RomanizeStation(cleanJa), cleanJa
			}
		}
		return Stop{Name: en, NameJa: ja, Code: p.StopCode, Lat: p.Lat, Lng: p.Lon}
	}

	journeys := []Journey{}
	seen := make(map[string]bool)

	for _, it := range plan.Itineraries {
		j := Journey{
			Minutes:   it.Duration / 60,
			Transfers: it.Transfers,
			Departs:   it.StartTime.In(japan).Format("15:04"),
			Arrives:   it.EndTime.In(japan).Format("15:04"),
		}

		var signature []string
		for i, leg := range it.Legs {
			l := JourneyLeg{
				Mode:    modeName(leg.Mode),
				From:    stop(leg.From),
				To:      stop(leg.To),
				Departs: leg.From.Departure.In(japan).Format("15:04"),
				Arrives: leg.To.Arrival.In(japan).Format("15:04"),
				Minutes: leg.Duration / 60,
				Path:    decodePath(leg.LegGeometry.Points, leg.LegGeometry.Precision),
			}

			if leg.Mode == "WALK" {
				names := make([]string, len(leg.Steps))
				for k, s := range leg.Steps {
					names[k] = s.StreetName
				}
				l.DistanceM = int(leg.Distance)
				l.Via = viaNames(names)
				// The exit: a walk that starts at a station (every walk but
				// the first) leaves it, so the first exit named is the one;
				// the first walk goes into a station, so it's the last.
				l.Exit = exitName(names, i > 0)
				if i < len(it.Legs)-1 {
					l.Platform = platform(names)
				}
				j.WalkMinutes += l.Minutes
			} else {
				l.Line, l.LineJa = journeyLine(leg.RouteShortName, leg.RouteLongName)
				l.LineCode = lineCode(leg.RouteShortName)
				if leg.RouteColor != "" {
					l.Color = "#" + strings.ToLower(leg.RouteColor)
				}
				l.Operator = operatorName(leg.AgencyName, leg.Mode)
				l.Towards = towards(leg.Headsign)
				if hasJapaneseRune(l.Towards) {
					cleanTowards := CleanStationName(l.Towards)
					if c.Stations != nil {
						if en := c.Stations.EnglishWithin(cleanTowards, leg.To.Lat, leg.To.Lon, 100); en != "" {
							l.Towards = en
						} else {
							l.Towards = RomanizeStation(cleanTowards)
						}
					} else {
						l.Towards = RomanizeStation(cleanTowards)
					}
				}
				l.Stops = len(leg.IntermediateStops) + 1
				signature = append(signature, l.Operator+" "+l.Line)
			}
			j.Legs = append(j.Legs, l)
		}

		// Like Route: Transitous often returns the same lines at different
		// times, so keep only genuinely different routes.
		sig := strings.Join(signature, "+")
		if seen[sig] {
			continue
		}
		seen[sig] = true

		journeys = append(journeys, j)
		if len(journeys) == 3 {
			break
		}
	}

	c.store(key, journeys, 10*time.Minute)
	return journeys, nil
}

// toeiLines translates the Toei subway's line names, which the data has only
// in Japanese.
var toeiLines = map[string]string{
	"浅草線":  "Toei Asakusa Line",
	"三田線":  "Toei Mita Line",
	"新宿線":  "Toei Shinjuku Line",
	"大江戸線": "Toei Oedo Line",
}

// journeyLine splits a line's name into English and Japanese: the Tokyo data
// has both in one ("JR中央・総武緩行線 JR Chūō-Sobu Local Line"). Without an
// English name, we translate the ones we know and otherwise fall back to
// lineName, which handles the Kansai data's train names.
func journeyLine(short, long string) (en, ja string) {
	if long != "" {
		en, ja = splitName(long)
		if ja == "" && hasJapaneseRune(en) {
			ja = en
			en = toeiLines[en]
		}
		if en != "" {
			return en, ja
		}
	}
	// Kansai train names end in a trip number ("Miyakoji Rapid 601"), which
	// isn't on any sign.
	return strings.TrimRight(lineName(short, long), " 0123456789"), ja
}

// modeName turns Transitous's modes into the few the app shows.
func modeName(mode string) string {
	switch mode {
	case "WALK":
		return "walk"
	case "HIGHSPEED_RAIL":
		return "shinkansen"
	case "SUBWAY", "METRO":
		return "subway"
	case "TRAM", "CABLE_CAR", "FUNICULAR", "AERIAL_LIFT":
		return "tram"
	case "BUS", "COACH":
		return "bus"
	case "FERRY":
		return "ferry"
	case "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "SUBURBAN":
		return "train"
	}
	return "other"
}

// splitName splits Transitous's bilingual station names, "千駄ケ谷
// Sendagaya", into English and Japanese. A name in only one language is
// returned as the English name.
func splitName(name string) (en, ja string) {
	i := strings.IndexByte(name, ' ')
	if i > 0 && hasJapaneseRune(name[:i]) && !hasJapaneseRune(name[i+1:]) {
		return strings.TrimSpace(name[i+1:]), strings.TrimSpace(name[:i])
	}
	if i > 0 && !hasJapaneseRune(name[:i]) && hasJapaneseRune(name[i+1:]) {
		return strings.TrimSpace(name[:i]), strings.TrimSpace(name[i+1:])
	}
	return name, ""
}

func hasJapaneseRune(s string) bool {
	for _, r := range s {
		if unicode.In(r, unicode.Hiragana, unicode.Katakana, unicode.Han) {
			return true
		}
	}
	return false
}

// lineCode keeps a short line code like "JB" or "G", which matches the
// signs. Long numeric trip ids (in the Kansai data) aren't codes.
func lineCode(short string) string {
	if len(short) > 0 && len(short) <= 3 && strings.Trim(short, "0123456789") != "" {
		return short
	}
	return ""
}

// towards cleans a headsign like "(普通 Local) 津田沼 Tsudanuma" down to the
// destination, "Tsudanuma".
func towards(headsign string) string {
	if i := strings.LastIndex(headsign, ")"); i >= 0 {
		headsign = strings.TrimSpace(headsign[i+1:])
	}
	en, _ := splitName(headsign)
	return en
}

// Station exits in OSM path names look like "浅草駅;A1出入口" or "京都駅;八条口".
var exitRX = regexp.MustCompile(`^(.+?)(番?出入口|番?出口)$|^(.{1,6}口)$`)

// exitName finds the station exit named on a walk. Walking into a station
// (first is false), the last exit named is the one you go in by; walking out
// (first is true), it's the first.
func exitName(streetNames []string, first bool) string {
	var exits []string
	for _, name := range streetNames {
		for _, part := range strings.Split(name, ";") {
			m := exitRX.FindStringSubmatch(strings.TrimSpace(part))
			switch {
			case m == nil:
			case m[1] != "":
				exits = append(exits, m[1]) // "A1出入口" -> "A1"
			case m[3] != "" && !strings.HasSuffix(m[3], "改札口"):
				exits = append(exits, m[3]) // "八条口" stays as it is
			}
		}
	}
	if len(exits) == 0 {
		return ""
	}
	if first {
		return exits[0]
	}
	return exits[len(exits)-1]
}

var platformRX = regexp.MustCompile(`^(\d+)番線$`)

// platform finds the platform at the end of a walk to a train: a path named
// "...;2階;1番線" within the walk's last few steps. Anything earlier is more
// likely the platform you arrived on.
func platform(streetNames []string) string {
	start := max(0, len(streetNames)-3)
	for i := len(streetNames) - 1; i >= start; i-- {
		for _, part := range strings.Split(streetNames[i], ";") {
			if m := platformRX.FindStringSubmatch(strings.TrimSpace(part)); m != nil {
				return m[1]
			}
		}
	}
	return ""
}

// viaNames lists the named streets and paths on a walk, in order, once each,
// leaving out station corridors (those are covered by the exit).
func viaNames(streetNames []string) []string {
	var via []string
	for _, name := range streetNames {
		if name == "" || strings.Contains(name, ";") || strings.Contains(name, "駅") || strings.Contains(name, "駐車場") {
			continue
		}
		if len(via) == 0 || via[len(via)-1] != name {
			via = append(via, name)
		}
		if len(via) == 3 {
			break
		}
	}
	return via
}

// decodePath decodes an encoded polyline (Google's format, at the given
// precision: Transitous uses 7 decimal places) into [lat, lng] points, keeping
// at most maxPathPoints of them.
func decodePath(encoded string, precision int) [][2]float64 {
	if precision == 0 {
		precision = 5
	}
	factor := 1.0
	for range precision {
		factor *= 10
	}

	// Never nil: an empty path is [] in the JSON, not null.
	points := [][2]float64{}
	var lat, lng int64
	for i := 0; i < len(encoded); {
		for _, coord := range []*int64{&lat, &lng} {
			var result, shift int64
			for i < len(encoded) {
				b := int64(encoded[i]) - 63
				i++
				result |= (b & 0x1f) << shift
				shift += 5
				if b < 0x20 {
					break
				}
			}
			if result&1 != 0 {
				*coord += ^(result >> 1)
			} else {
				*coord += result >> 1
			}
		}
		points = append(points, [2]float64{float64(lat) / factor, float64(lng) / factor})
	}

	if len(points) <= maxPathPoints {
		return points
	}
	// Keep every nth point, and always the last one.
	step := (len(points) + maxPathPoints - 1) / maxPathPoints
	thinned := make([][2]float64, 0, maxPathPoints+1)
	for i := 0; i < len(points); i += step {
		thinned = append(thinned, points[i])
	}
	if thinned[len(thinned)-1] != points[len(points)-1] {
		thinned = append(thinned, points[len(points)-1])
	}
	return thinned
}

// DepartAt turns a departure time like "09:00" (Japan time) into a time: today
// if it's still to come (or was less than an hour ago), otherwise tomorrow.
// An empty or unreadable time means now.
func DepartAt(now time.Time, hhmm string) time.Time {
	if hhmm == "" {
		return now
	}

	t, err := time.ParseInLocation("15:04", hhmm, japan)
	if err != nil {
		return now
	}

	now = now.In(japan)
	at := time.Date(now.Year(), now.Month(), now.Day(), t.Hour(), t.Minute(), 0, 0, japan)
	if at.Before(now.Add(-time.Hour)) {
		at = at.AddDate(0, 0, 1)
	}
	return at
}
