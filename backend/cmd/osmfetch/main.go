package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"os"
	"slices"
	"strings"
	"time"
)

// osmfetch downloads the restaurants and shops that matter to our travellers
// from OpenStreetMap, and writes them to osmdata/places.json. That file is
// committed and loaded into PostgreSQL by `make db/seed`, so the app never
// depends on the (often overloaded) public Overpass servers at runtime.
//
// Run it occasionally to refresh the data:
//
//	go run ./cmd/osmfetch
//
// It takes a few minutes: one query per city, one city at a time, to be kind
// to the volunteer-run servers.

// cities are the areas we fetch, as south,west,north,east bounding boxes.
var cities = []struct {
	name string
	bbox string
}{
	{"Tokyo", "35.53,139.56,35.82,139.92"},
	{"Kyoto", "34.88,135.65,35.08,135.85"},
	{"Osaka", "34.58,135.40,34.76,135.60"},
	{"Nara", "34.64,135.76,34.72,135.87"},
	{"Sapporo", "42.98,141.25,43.12,141.45"},
}

// servers are public Overpass instances, tried in order. In testing, most
// of them timed out and only the mail.ru one answered (slowly).
var servers = []string{
	"https://maps.mail.ru/osm/tools/overpass/api/interpreter",
	"https://overpass.kumi.systems/api/interpreter",
	"https://overpass-api.de/api/interpreter",
}

// The query: food places with diet tags or Indian-style cuisine (Desi Match
// candidates), and the shops travellers ask about (anime, figures, toys,
// hobby, 100-yen stores).
const queryTemplate = `[out:json][timeout:240][bbox:%s];
(nwr["amenity"~"^(restaurant|fast_food|cafe|food_court)$"]["diet:vegetarian"~"yes|only"];
 nwr["amenity"~"^(restaurant|fast_food|cafe|food_court)$"]["diet:vegan"~"yes|only"];
 nwr["amenity"~"^(restaurant|fast_food|cafe|food_court)$"]["diet:halal"~"yes|only"];
 nwr["amenity"~"^(restaurant|fast_food)$"]["cuisine"~"indian|nepalese|sri_lankan|pakistani|bangladeshi|vegetarian|vegan|curry"];
 nwr["shop"~"^(anime|collector|games|model|hobby|toys|variety_store)$"];);
out center tags;`

// keptTags are the OSM tags we keep. Everything else is dropped to keep the
// snapshot small (and free of personal data like phone numbers).
var keptTags = []string{
	"amenity", "shop", "cuisine", "brand", "brand:en",
	"diet:vegetarian", "diet:vegan", "diet:halal",
	"opening_hours", "website",
}

// Place is one record in places.json. The shape must match data.Place's JSON.
type Place struct {
	ID     string            `json:"id"`
	City   string            `json:"city"`
	Name   string            `json:"name"`
	NameEn string            `json:"name_en,omitempty"`
	Lat    float64           `json:"lat"`
	Lng    float64           `json:"lng"`
	Tags   map[string]string `json:"tags"`
}

func main() {
	var out, userAgent, only string
	flag.StringVar(&out, "out", "osmdata/places.json", "Output file")
	flag.StringVar(&userAgent, "user-agent", "KonnichiWah/1.0 (hackathon prototype; github.com/Sachin0P/Konnichi-Wah)", "User-Agent sent to Overpass (no personal data)")
	flag.StringVar(&only, "cities", "", "Comma-separated cities to fetch (default: all)")
	flag.Parse()

	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))

	// Connect over IPv4 only. In testing, IPv6 connections to the Overpass
	// servers stalled for minutes (through a VPN), while IPv4 answered in
	// seconds.
	dialer := &net.Dialer{Timeout: 30 * time.Second}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.DialContext = func(ctx context.Context, _, addr string) (net.Conn, error) {
		return dialer.DialContext(ctx, "tcp4", addr)
	}
	client := &http.Client{Timeout: 3 * time.Minute, Transport: transport}

	// Start from the existing snapshot, so a city we can't fetch this time
	// keeps its old data rather than disappearing.
	byCity := make(map[string][]Place)
	if js, err := os.ReadFile(out); err == nil {
		var existing []Place
		if err := json.Unmarshal(js, &existing); err != nil {
			logger.Error("existing snapshot is not valid JSON", "file", out, "error", err)
			os.Exit(1)
		}
		for _, p := range existing {
			byCity[p.City] = append(byCity[p.City], p)
		}
	}

	failed := 0
	for _, city := range cities {
		if only != "" && !slices.Contains(strings.Split(only, ","), city.name) {
			continue
		}

		got, err := fetchWithRetries(client, userAgent, city.name, city.bbox)
		if err != nil {
			failed++
			logger.Error("fetch failed, keeping the old data", "city", city.name, "old_places", len(byCity[city.name]), "error", err)
			continue
		}
		logger.Info("fetched", "city", city.name, "places", len(got))
		byCity[city.name] = got

		// Save after every city, so a later failure never loses earlier work.
		if err := write(out, byCity); err != nil {
			logger.Error(err.Error())
			os.Exit(1)
		}
	}

	if failed > 0 {
		logger.Error("some cities failed; run again later with -cities", "failed", failed)
		os.Exit(1)
	}
}

// fetchWithRetries tries every server, up to three rounds, pausing between
// rounds: a 504 from a busy server is usually gone a minute later.
func fetchWithRetries(client *http.Client, userAgent, city, bbox string) ([]Place, error) {
	var err error
	for round := 1; round <= 3; round++ {
		var places []Place
		places, err = fetchCity(client, userAgent, city, bbox)
		if err == nil {
			return places, nil
		}
		if round < 3 {
			slog.Warn("every server failed, waiting before another round", "city", city, "round", round)
			time.Sleep(time.Duration(round) * 30 * time.Second)
		}
	}
	return nil, err
}

// write saves the snapshot, sorted so that re-running the fetch gives a
// readable diff.
func write(out string, byCity map[string][]Place) error {
	var places []Place
	for _, ps := range byCity {
		places = append(places, ps...)
	}
	slices.SortFunc(places, func(a, b Place) int {
		return strings.Compare(a.City+a.ID, b.City+b.ID)
	})

	js, err := json.MarshalIndent(places, "", " ")
	if err != nil {
		return err
	}
	return os.WriteFile(out, append(js, '\n'), 0o644)
}

func fetchCity(client *http.Client, userAgent, city, bbox string) ([]Place, error) {
	query := fmt.Sprintf(queryTemplate, bbox)

	var lastErr error
	for _, server := range servers {
		start := time.Now()
		body, err := post(client, userAgent, server, query)
		if err != nil {
			lastErr = fmt.Errorf("%s: %w", server, err)
			slog.Warn("server failed, trying the next", "server", server, "after", time.Since(start).Round(time.Second), "error", err)
			continue
		}

		var res struct {
			Elements []struct {
				Type   string  `json:"type"`
				ID     int64   `json:"id"`
				Lat    float64 `json:"lat"`
				Lon    float64 `json:"lon"`
				Center *struct{ Lat, Lon float64 }
				Tags   map[string]string `json:"tags"`
			} `json:"elements"`
		}
		if err := json.Unmarshal(body, &res); err != nil {
			lastErr = fmt.Errorf("%s: decoding: %w", server, err)
			continue
		}

		var places []Place
		for _, e := range res.Elements {
			name := e.Tags["name"]
			if name == "" {
				continue // a nameless place is no use to a traveller
			}

			lat, lng := e.Lat, e.Lon
			if e.Center != nil { // ways and relations come with a centre point
				lat, lng = e.Center.Lat, e.Center.Lon
			}

			tags := make(map[string]string)
			for _, k := range keptTags {
				if v := e.Tags[k]; v != "" {
					tags[k] = v
				}
			}

			places = append(places, Place{
				ID:     fmt.Sprintf("%s/%d", e.Type, e.ID),
				City:   city,
				Name:   name,
				NameEn: e.Tags["name:en"],
				Lat:    lat,
				Lng:    lng,
				Tags:   tags,
			})
		}
		return places, nil
	}

	return nil, lastErr
}

func post(client *http.Client, userAgent, server, query string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, server, strings.NewReader(url.Values{"data": {query}}.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("User-Agent", userAgent)

	res, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	body, err := io.ReadAll(io.LimitReader(res.Body, 64<<20))
	if err != nil {
		return nil, err
	}
	if res.StatusCode != http.StatusOK {
		return nil, errors.New("status " + res.Status)
	}
	return body, nil
}
