// Package photon searches OpenStreetMap with Photon (photon.komoot.io), for
// places our snapshot doesn't cover: "halal grocery", "laundromat", "Muji".
//
// Photon is free and needs no key, but it's a shared service with a fair-use
// policy, and in testing it often failed from behind a VPN. So we cache
// results, keep the timeout short, and treat any failure as "no results"
// further up: the snapshot covers the essentials anyway.
package photon

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

const DefaultBaseURL = "https://photon.komoot.io/api/"

// Place is one search result.
type Place struct {
	Name    string  `json:"name"`
	Kind    string  `json:"kind"` // the OSM tag value, e.g. "laundry", "supermarket"
	Address string  `json:"address,omitempty"`
	Lat     float64 `json:"lat"`
	Lng     float64 `json:"lng"`
}

type Client struct {
	baseURL   string
	userAgent string
	http      *http.Client

	mu    sync.Mutex
	cache map[string]cached
}

type cached struct {
	places  []Place
	expires time.Time
}

func New(baseURL, userAgent string, timeout time.Duration) *Client {
	return &Client{
		baseURL:   baseURL,
		userAgent: userAgent,
		http:      &http.Client{Timeout: timeout},
		cache:     make(map[string]cached),
	}
}

// categories map common category words to OpenStreetMap tags. Photon's text
// search matches words in names, so "supermarket" finds shops called
// "... Supermarket" rather than the supermarkets nearby (which are called
// ライフ or 西友). For these words we use its reverse search instead, which
// finds the nearest places with the tag.
var categories = map[string]string{
	"supermarket":      "shop:supermarket",
	"grocery":          "shop:supermarket",
	"laundromat":       "shop:laundry",
	"laundry":          "shop:laundry",
	"coin laundry":     "shop:laundry",
	"bank":             "amenity:bank",
	"post office":      "amenity:post_office",
	"cafe":             "amenity:cafe",
	"bakery":           "shop:bakery",
	"mosque":           "amenity:place_of_worship",
	"hotel":            "tourism:hotel",
	"park":             "leisure:park",
	"electronics":      "shop:electronics",
	"bookshop":         "shop:books",
	"bookstore":        "shop:books",
	"department store": "shop:department_store",
	"luggage storage":  "amenity:luggage_locker",
	"coin locker":      "amenity:luggage_locker",
}

// searchBox is how far around the point we search, in degrees: about 5 km.
// Photon would otherwise happily return a place with the same name in
// another prefecture.
const searchBox = 0.045

// Search finds up to limit places matching query near a point, nearest
// first as Photon ranks them.
func (c *Client) Search(ctx context.Context, query string, lat, lng float64, limit int) ([]Place, error) {
	key := fmt.Sprintf("%s|%.3f,%.3f|%d", strings.ToLower(query), lat, lng, limit)

	c.mu.Lock()
	if e, ok := c.cache[key]; ok && time.Now().Before(e.expires) {
		c.mu.Unlock()
		return e.places, nil
	}
	c.mu.Unlock()

	endpoint := c.baseURL
	params := url.Values{
		"lat":   {fmt.Sprintf("%.5f", lat)},
		"lon":   {fmt.Sprintf("%.5f", lng)},
		"limit": {fmt.Sprint(limit)},
		"lang":  {"en"},
	}
	if tag, ok := categories[strings.ToLower(strings.TrimSpace(query))]; ok {
		// Reverse search: the nearest places with this tag, within 3 km.
		endpoint = strings.TrimSuffix(c.baseURL, "api/") + "reverse"
		params.Set("osm_tag", tag)
		params.Set("radius", "3")
	} else {
		params.Set("q", query)
		params.Set("bbox", fmt.Sprintf("%.5f,%.5f,%.5f,%.5f", lng-searchBox, lat-searchBox, lng+searchBox, lat+searchBox))
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint+"?"+params.Encode(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", c.userAgent)

	res, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	if res.StatusCode != http.StatusOK {
		return nil, errors.New("photon: status " + res.Status)
	}

	body, err := io.ReadAll(io.LimitReader(res.Body, 1<<20))
	if err != nil {
		return nil, err
	}

	var out struct {
		Features []struct {
			Geometry struct {
				Coordinates [2]float64 `json:"coordinates"` // [lng, lat]
			} `json:"geometry"`
			Properties struct {
				Name        string `json:"name"`
				OSMValue    string `json:"osm_value"`
				Street      string `json:"street"`
				HouseNumber string `json:"housenumber"`
				District    string `json:"district"`
				City        string `json:"city"`
			} `json:"properties"`
		} `json:"features"`
	}
	if err := json.Unmarshal(body, &out); err != nil {
		return nil, fmt.Errorf("photon: decoding response: %w", err)
	}

	places := []Place{}
	for _, f := range out.Features {
		p := f.Properties
		if p.Name == "" {
			continue
		}
		var address []string
		for _, part := range []string{strings.TrimSpace(p.Street + " " + p.HouseNumber), p.District, p.City} {
			if part != "" {
				address = append(address, part)
			}
		}
		places = append(places, Place{
			Name:    p.Name,
			Kind:    p.OSMValue,
			Address: strings.Join(address, ", "),
			Lat:     f.Geometry.Coordinates[1],
			Lng:     f.Geometry.Coordinates[0],
		})
	}

	c.mu.Lock()
	if len(c.cache) > 500 {
		clear(c.cache)
	}
	c.cache[key] = cached{places: places, expires: time.Now().Add(time.Hour)}
	c.mu.Unlock()

	return places, nil
}
