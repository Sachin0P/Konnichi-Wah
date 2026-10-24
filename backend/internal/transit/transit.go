package transit

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"slices"
	"strings"
	"sync"
	"time"
	"unicode"
)

// DefaultBaseURL is the public Transitous API: free, no key, run by
// volunteers for open-source and non-profit use (https://transitous.org/api/).
// Keep request volume low; that's what the cache below is for.
const DefaultBaseURL = "https://api.transitous.org/api/v1"

// ErrNotFound means a place name couldn't be matched to a location in Japan.
var ErrNotFound = errors.New("transit: place not found")

// japan is the time zone for every time we show.
var japan = time.FixedZone("JST", 9*60*60)

// LatLng is a point on the map.
type LatLng struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

// cityCentres biases place searches towards the right city. Without it,
// "Todai-ji" can match a place in Brazil and "Nara Park" a hotel.
var cityCentres = map[string]LatLng{
	"tokyo":     {Lat: 35.681, Lng: 139.767},
	"yokohama":  {Lat: 35.466, Lng: 139.622},
	"kamakura":  {Lat: 35.319, Lng: 139.550},
	"hakone":    {Lat: 35.233, Lng: 139.107},
	"nikko":     {Lat: 36.749, Lng: 139.598},
	"kyoto":     {Lat: 34.986, Lng: 135.759},
	"nara":      {Lat: 34.685, Lng: 135.805},
	"osaka":     {Lat: 34.702, Lng: 135.496},
	"kobe":      {Lat: 34.690, Lng: 135.196},
	"himeji":    {Lat: 34.827, Lng: 134.691},
	"hiroshima": {Lat: 34.398, Lng: 132.475},
	"kanazawa":  {Lat: 36.578, Lng: 136.648},
	"fukuoka":   {Lat: 33.590, Lng: 130.421},
	"sapporo":   {Lat: 43.068, Lng: 141.351},
	"otaru":     {Lat: 43.197, Lng: 140.994},
}

// CityCentre returns the centre of a city we know, or nil.
func CityCentre(city string) *LatLng {
	if c, ok := cityCentres[strings.ToLower(strings.TrimSpace(city))]; ok {
		return &c
	}
	return nil
}

// Place is a geocoded name.
type Place struct {
	Name     string
	Location LatLng
}

// Option is one way to make a journey, simplified for the LLM: the times are
// already in Japan time and the walking legs are kept so it can mention them.
type Option struct {
	Minutes   int    `json:"minutes"`
	Transfers int    `json:"transfers"`
	Departs   string `json:"departs"`
	Arrives   string `json:"arrives"`
	Legs      []Leg  `json:"legs"`
}

// Label replaces Transitous's "START" and "END" placeholders with the real
// names of the journey's two ends. Without it, a final "walk 1 min -> END"
// leg reads as if it reached the sight, when it only reaches the station
// exit.
func (o *Option) Label(from, to string) {
	for i := range o.Legs {
		if o.Legs[i].From == "START" {
			o.Legs[i].From = from
		}
		if o.Legs[i].To == "END" {
			o.Legs[i].To = to
		}
	}
}

type Leg struct {
	Mode     string `json:"mode"`
	Line     string `json:"line,omitempty"`
	Operator string `json:"operator,omitempty"`
	Towards  string `json:"towards,omitempty"`
	From     string `json:"from"`
	To       string `json:"to"`
	Departs  string `json:"departs"`
	Arrives  string `json:"arrives"`
	Minutes  int    `json:"minutes"`
	// Exit is the station exit a walk uses ("A1"), when the map data names it.
	Exit string `json:"exit,omitempty"`
}

// Client calls Transitous. It caches results for a while: a traveller asking
// about the same journey twice shouldn't cost the volunteers two lookups.
//
// Stations, if set, gives English names to stations the timetables only name
// in Japanese.
type Client struct {
	Stations *StationNames

	baseURL   string
	userAgent string
	http      *http.Client

	mu    sync.Mutex
	cache map[string]cacheEntry
}

type cacheEntry struct {
	value   any
	expires time.Time
}

// New returns a Client. userAgent should name the app (Transitous asks for a
// descriptive one); never put personal data in it.
func New(baseURL, userAgent string, timeout time.Duration) *Client {
	return &Client{
		baseURL:   strings.TrimRight(baseURL, "/"),
		userAgent: userAgent,
		http:      &http.Client{Timeout: timeout},
		cache:     make(map[string]cacheEntry),
	}
}

// Geocode finds a place in Japan by name. near, if given, biases the search
// towards a city: without it "Todai-ji" can match a place in Brazil.
//
// Names from the planner are often compound, like "Senso-ji Temple, Asakusa"
// or "Nara Park and Todai-ji", and the search engine matched the first one
// to Hou-on-ji Temple, 4 km away. So Geocode tries the full name and then
// simpler versions (see nameVariants), and only accepts a result whose name
// actually matches what it searched for. If nothing matches well, it falls
// back to the first result for the full name: a reasonable guess when
// someone asks how to get somewhere.
func (c *Client) Geocode(ctx context.Context, name string, near *LatLng) (*Place, error) {
	return c.geocode(ctx, name, near, false)
}

// GeocodeExact is like Geocode, but never guesses: it returns ErrNotFound
// unless a result's name matches. Use it for coordinates we save, where no
// location is better than a wrong one (in testing, "Nirvanam, Kamiyacho"
// guessed a point 6 km away, and a CoCo Ichibanya chain branch 60 km away).
func (c *Client) GeocodeExact(ctx context.Context, name string, near *LatLng) (*Place, error) {
	return c.geocode(ctx, name, near, true)
}

func (c *Client) geocode(ctx context.Context, name string, near *LatLng, exact bool) (*Place, error) {
	key := fmt.Sprintf("geo|%s|%v|%v", strings.ToLower(strings.TrimSpace(name)), near, exact)
	if v, ok := c.cached(key); ok {
		return v.(*Place), nil
	}

	var fallback *Place
	for _, variant := range nameVariants(name) {
		candidates, err := c.search(ctx, variant, near)
		if err != nil {
			return nil, err
		}
		if len(candidates) == 0 {
			continue
		}
		if fallback == nil {
			fallback = &candidates[0]
		}
		if best := bestMatch(variant, candidates); best != nil {
			c.store(key, best, 24*time.Hour)
			return best, nil
		}
	}

	if fallback == nil || exact {
		return nil, ErrNotFound
	}
	c.store(key, fallback, 24*time.Hour)
	return fallback, nil
}

// maxDistanceKm is how far from the city centre a match may be. 40 km
// covers Tokyo out to Mitaka and beyond; Hakone, Kamakura and so on have
// their own centres.
const maxDistanceKm = 40

func stripMacrons(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch r {
		case 'ō', 'ó', 'ò', 'ö', 'ô':
			b.WriteRune('o')
		case 'Ō', 'Ó', 'Ò', 'Ö', 'Ô':
			b.WriteRune('O')
		case 'ū', 'ú', 'ù', 'ü', 'û':
			b.WriteRune('u')
		case 'Ū', 'Ú', 'Ù', 'Ü', 'Û':
			b.WriteRune('U')
		case 'ā', 'á', 'à', 'ä', 'â':
			b.WriteRune('a')
		case 'Ā', 'Á', 'À', 'Ä', 'Â':
			b.WriteRune('A')
		case 'ē', 'é', 'è', 'ë', 'ê':
			b.WriteRune('e')
		case 'Ē', 'É', 'È', 'Ë', 'Ê':
			b.WriteRune('E')
		case 'ī', 'í', 'ì', 'ï', 'î':
			b.WriteRune('i')
		case 'Ī', 'Í', 'Ì', 'Ï', 'Î':
			b.WriteRune('I')
		default:
			b.WriteRune(r)
		}
	}
	return b.String()
}

// search runs one geocoding query and returns the matches in Japan (and near
// the city, if given), in the search engine's order. A name ending in
// "station" (or 駅) is searched as a station stop without the suffix, so
// "Shinjuku Station" doesn't match Shinjuku Fire Station.
func (c *Client) search(ctx context.Context, name string, near *LatLng) ([]Place, error) {
	name = stripMacrons(name)
	query, station := stripStation(name)

	params := url.Values{"text": {query}}
	if station {
		params.Set("type", "STOP")
	}
	if near != nil {
		params.Set("place", fmt.Sprintf("%.4f,%.4f", near.Lat, near.Lng))
	}

	var matches []struct {
		Name    string  `json:"name"`
		Type    string  `json:"type"`
		Country string  `json:"country"`
		Lat     float64 `json:"lat"`
		Lon     float64 `json:"lon"`
	}

	if err := c.get(ctx, "/geocode", params, &matches); err != nil {
		return nil, err
	}

	// Keep matches in Japan and, with a city to aim for, near it (not a
	// "Nara Park" in Tokyo when we're in Nara).
	var candidates []Place
	for _, m := range matches {
		if m.Country != "JP" {
			continue
		}
		if near != nil && distanceKm(*near, LatLng{m.Lat, m.Lon}) > maxDistanceKm {
			continue
		}
		candidates = append(candidates, Place{Name: m.Name, Location: LatLng{m.Lat, m.Lon}})
	}
	return candidates, nil
}

// nameVariants returns the name, then simpler versions of it to try: before
// a comma ("Senso-ji Temple, Asakusa" -> "Senso-ji Temple"), before " and "
// ("Nara Park and Todai-ji" -> "Nara Park") and before brackets
// ("Kinkaku-ji (Golden Pavilion)" -> "Kinkaku-ji"), and each of those
// without a trailing "Temple" or "Shrine". The geocoder matched "Senso-ji
// Temple" to Senzo-ji Temple in Chiba, but found "Senso-ji" straight away.
func nameVariants(name string) []string {
	name = strings.TrimSpace(name)
	variants := []string{name}

	add := func(v string) {
		v = strings.TrimSpace(v)
		if v != "" && !slices.Contains(variants, v) {
			variants = append(variants, v)
		}
	}

	for _, sep := range []string{",", " and ", " & ", "(", "（", " - ", " – "} {
		if i := strings.Index(name, sep); i > 0 {
			add(name[:i])
		}
	}

	for _, v := range slices.Clone(variants) {
		lower := strings.ToLower(v)
		for _, suffix := range []string{" temple", " shrine", " electric town", " station"} {
			if strings.HasSuffix(lower, suffix) {
				add(v[:len(v)-len(suffix)])
			}
		}
	}
	return variants
}

// bestMatch returns the first candidate whose name matches the query, or nil.
// Names match if one contains the other once normalised, ignoring generic
// words: "Sensō-ji" matches "Senso-ji Temple", but "Hou-on-ji Temple" doesn't.
// An exact match wins over a partial one.
func bestMatch(query string, candidates []Place) *Place {
	q := normalizeName(stripGeneric(query))
	if len(q) < 3 {
		return nil
	}

	for i := range candidates {
		if normalizeName(stripGeneric(candidates[i].Name)) == q {
			return &candidates[i]
		}
	}
	for i := range candidates {
		n := normalizeName(stripGeneric(candidates[i].Name))
		if len(n) >= 3 && (strings.Contains(q, n) || strings.Contains(n, q)) {
			return &candidates[i]
		}
	}
	return nil
}

// stripGeneric removes words that appear in many place names, so they don't
// decide a match on their own.
func stripGeneric(s string) string {
	out := strings.ToLower(s)
	for _, w := range []string{"temple", "shrine", "station", "park", "garden", "national", "museum"} {
		out = strings.ReplaceAll(out, w, "")
	}
	return out
}

// normalizeName makes names comparable: "Sensō-ji" and "senso ji" match.
//
// Only letters and digits are kept, so punctuation never decides a match:
// "Nirvanam, Kamiyacho" must match "Nirvanam Kamiyacho".
func normalizeName(s string) string {
	s = strings.NewReplacer("ō", "o", "ū", "u", "ā", "a", "ē", "e", "ī", "i").Replace(strings.ToLower(s))
	return strings.Map(func(r rune) rune {
		if unicode.IsLetter(r) || unicode.IsDigit(r) {
			return r
		}
		return -1
	}, s)
}

// Route returns up to three ways to travel between two points, leaving at
// departAt. Transitous names the two ends "START" and "END"; we leave those
// for the caller to replace with real names (see Option.Label), since only
// the caller knows what the points are.
func (c *Client) Route(ctx context.Context, from, to LatLng, departAt time.Time) ([]Option, error) {
	// Round the time to 10 minutes so that repeated questions hit the cache.
	departAt = departAt.Truncate(10 * time.Minute)

	key := fmt.Sprintf("route|%.4f,%.4f|%.4f,%.4f|%d", from.Lat, from.Lng, to.Lat, to.Lng, departAt.Unix())
	if v, ok := c.cached(key); ok {
		return v.([]Option), nil
	}

	params := url.Values{
		"fromPlace": {fmt.Sprintf("%.5f,%.5f", from.Lat, from.Lng)},
		"toPlace":   {fmt.Sprintf("%.5f,%.5f", to.Lat, to.Lng)},
		"time":      {departAt.UTC().Format(time.RFC3339)},
	}

	var plan struct {
		Itineraries []struct {
			Duration  int `json:"duration"`
			Transfers int `json:"transfers"`
			StartTime time.Time
			EndTime   time.Time
			Legs      []struct {
				Mode           string `json:"mode"`
				Duration       int    `json:"duration"`
				RouteShortName string `json:"routeShortName"`
				RouteLongName  string `json:"routeLongName"`
				AgencyName     string `json:"agencyName"`
				Headsign       string `json:"headsign"`
				From           struct {
					Name      string    `json:"name"`
					Departure time.Time `json:"departure"`
				} `json:"from"`
				To struct {
					Name    string    `json:"name"`
					Arrival time.Time `json:"arrival"`
				} `json:"to"`
				Steps []struct {
					StreetName string `json:"streetName"`
				} `json:"steps"`
			} `json:"legs"`
		} `json:"itineraries"`
	}

	err := c.get(ctx, "/plan", params, &plan)
	if err != nil {
		return nil, err
	}

	options := []Option{}
	seen := make(map[string]bool)

	for _, it := range plan.Itineraries {
		option := Option{
			Minutes:   it.Duration / 60,
			Transfers: it.Transfers,
			Departs:   it.StartTime.In(japan).Format("15:04"),
			Arrives:   it.EndTime.In(japan).Format("15:04"),
		}

		var signature []string
		for i, leg := range it.Legs {
			l := Leg{
				Mode:    strings.ToLower(leg.Mode),
				From:    leg.From.Name,
				To:      leg.To.Name,
				Departs: leg.From.Departure.In(japan).Format("15:04"),
				Arrives: leg.To.Arrival.In(japan).Format("15:04"),
				Minutes: leg.Duration / 60,
			}
			if leg.Mode == "WALK" {
				names := make([]string, len(leg.Steps))
				for k, s := range leg.Steps {
					names[k] = s.StreetName
				}
				l.Exit = exitName(names, i > 0) // see Directions
			} else {
				l.Line = lineName(leg.RouteShortName, leg.RouteLongName)
				l.Operator = operatorName(leg.AgencyName, leg.Mode)
				l.Towards = leg.Headsign
				signature = append(signature, l.Operator+" "+l.Line)
			}
			option.Legs = append(option.Legs, l)
		}

		// Transitous often returns the same lines at different times. Keep
		// only genuinely different routes.
		sig := strings.Join(signature, "+")
		if seen[sig] {
			continue
		}
		seen[sig] = true

		options = append(options, option)
		if len(options) == 3 {
			break
		}
	}

	c.store(key, options, 10*time.Minute)
	return options, nil
}

func (c *Client) get(ctx context.Context, path string, params url.Values, dst any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path+"?"+params.Encode(), nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", c.userAgent)

	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()

	body, err := io.ReadAll(io.LimitReader(res.Body, 4_194_304))
	if err != nil {
		return err
	}

	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("transit: %s returned status %d", path, res.StatusCode)
	}

	return json.Unmarshal(body, dst)
}

func (c *Client) cached(key string) (any, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	entry, ok := c.cache[key]
	if !ok || time.Now().After(entry.expires) {
		delete(c.cache, key)
		return nil, false
	}
	return entry.value, true
}

func (c *Client) store(key string, value any, ttl time.Duration) {
	c.mu.Lock()
	defer c.mu.Unlock()

	// The cache only lives as long as the process and is small for a demo,
	// but don't let it grow without limit.
	if len(c.cache) > 1000 {
		clear(c.cache)
	}
	c.cache[key] = cacheEntry{value: value, expires: time.Now().Add(ttl)}
}

// operators translates the Japanese operator names in the Kansai data. (The
// Tokyo data already has English, e.g. "東京メトロ Tokyo Metro".)
var operators = map[string]string{
	"近畿日本鉄道":    "Kintetsu",
	"京阪電気鉄道":    "Keihan",
	"阪急電鉄":      "Hankyu",
	"阪神電気鉄道":    "Hanshin",
	"南海電気鉄道":    "Nankai",
	"京福電気鉄道":    "Randen (Keifuku)",
	"叡山電鉄":      "Eizan Railway",
	"大阪市高速電気軌道": "Osaka Metro",
	"西日本旅客鉄道":   "JR West",
	"東海旅客鉄道":    "JR Central",
}

// operatorName returns an English operator name. Kyoto's city transport
// bureau runs both the subway and the buses, so the mode decides which.
func operatorName(agency, mode string) string {
	if agency == "京都市交通局" {
		if mode == "BUS" {
			return "Kyoto City Bus"
		}
		return "Kyoto City Subway"
	}
	if en, ok := operators[agency]; ok {
		return en
	}
	return agency
}

// trainNames translates the Japanese train and service names we've seen.
var trainNames = strings.NewReplacer(
	"のぞみ", "Nozomi ", "ひかり", "Hikari ", "こだま", "Kodama ",
	"みずほ", "Mizuho ", "さくら", "Sakura ", "はやぶさ", "Hayabusa ",
	"みやこ路快速", "Miyakoji Rapid ", "大和路快速", "Yamatoji Rapid ",
	"新快速", "Special Rapid ", "快速", "Rapid ", "普通", "Local ",
	"号", "",
)

// lineName builds a readable line name. In the Kansai data the short name is
// sometimes just a numeric trip id ("7391371"), which means nothing to a
// traveller, so it's dropped. Service names are translated, and the
// full-width route letters (Ｄ, Ｑ) are removed.
func lineName(short, long string) string {
	if long != "" {
		return strings.TrimSpace(short + " " + long)
	}
	if strings.Trim(short, "0123456789") == "" {
		return ""
	}

	name := strings.TrimLeftFunc(short, func(r rune) bool { return r >= 'Ａ' && r <= 'Ｚ' })
	return strings.Join(strings.Fields(trainNames.Replace(name)), " ")
}

// stripStation removes a trailing "station" or 駅, and reports whether it did.
func stripStation(name string) (string, bool) {
	name = strings.TrimSpace(name)
	lower := strings.ToLower(name)

	for _, suffix := range []string{" station", " sta.", " stn", "駅"} {
		if strings.HasSuffix(lower, suffix) {
			return strings.TrimSpace(name[:len(name)-len(suffix)]), true
		}
	}
	return name, false
}

// distanceKm is the great-circle distance between two points.
func distanceKm(a, b LatLng) float64 {
	const earthRadiusKm = 6371
	rad := math.Pi / 180

	dLat := (b.Lat - a.Lat) * rad
	dLng := (b.Lng - a.Lng) * rad
	h := math.Sin(dLat/2)*math.Sin(dLat/2) + math.Cos(a.Lat*rad)*math.Cos(b.Lat*rad)*math.Sin(dLng/2)*math.Sin(dLng/2)

	return 2 * earthRadiusKm * math.Asin(math.Sqrt(h))
}
