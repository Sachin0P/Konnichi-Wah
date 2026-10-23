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
	"strings"
	"sync"
	"time"
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
}

// Client calls Transitous. It caches results for a while: a traveller asking
// about the same journey twice shouldn't cost the volunteers two lookups.
type Client struct {
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
// towards a city: without it "Todai-ji" can match a place in Brazil. A name
// ending in "station" (or 駅) is searched as a station stop, without the
// suffix, so "Shinjuku Station" doesn't match Shinjuku Fire Station.
func (c *Client) Geocode(ctx context.Context, name string, near *LatLng) (*Place, error) {
	query, station := stripStation(name)

	key := fmt.Sprintf("geo|%s|%v|%v", strings.ToLower(query), station, near)
	if v, ok := c.cached(key); ok {
		return v.(*Place), nil
	}

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

	err := c.get(ctx, "/geocode", params, &matches)
	if err != nil {
		return nil, err
	}

	for _, m := range matches {
		if m.Country != "JP" {
			continue
		}
		// With a city to aim for, skip matches far away from it (a "Nara
		// Park" in Tokyo when we're in Nara).
		if near != nil && distanceKm(*near, LatLng{m.Lat, m.Lon}) > 60 {
			continue
		}

		place := &Place{Name: m.Name, Location: LatLng{m.Lat, m.Lon}}
		c.store(key, place, 24*time.Hour)
		return place, nil
	}

	return nil, ErrNotFound
}

// Route returns up to three ways to travel between two points, leaving at
// departAt.
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
		for _, leg := range it.Legs {
			l := Leg{
				Mode:    strings.ToLower(leg.Mode),
				From:    leg.From.Name,
				To:      leg.To.Name,
				Departs: leg.From.Departure.In(japan).Format("15:04"),
				Arrives: leg.To.Arrival.In(japan).Format("15:04"),
				Minutes: leg.Duration / 60,
			}
			if leg.Mode != "WALK" {
				l.Line = strings.TrimSpace(leg.RouteShortName + " " + leg.RouteLongName)
				l.Operator = leg.AgencyName
				l.Towards = leg.Headsign
				signature = append(signature, l.Line)
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
