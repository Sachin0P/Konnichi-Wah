package data

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"math"
	"slices"
	"strings"
	"time"
)

// Place is a restaurant, shop or place to get help from OpenStreetMap. Tags
// holds the few OSM tags we keep, e.g. "cuisine": "indian",
// "diet:vegetarian": "only".
type Place struct {
	ID     string            `json:"id"`
	City   string            `json:"city"`
	Name   string            `json:"name"`
	NameEn string            `json:"name_en,omitempty"`
	Lat    float64           `json:"lat"`
	Lng    float64           `json:"lng"`
	Tags   map[string]string `json:"tags"`
}

// The kinds of place we search for. Food and shops are the Desi Match and
// shop searches; the help kinds are what a traveller needs when something
// goes wrong (or just a toilet).
const (
	KindFood = "food"
	KindShop = "shop"
)

// HelpKinds are the help kinds, for the API and the companion's tool.
var HelpKinds = []string{"hospital", "clinic", "pharmacy", "police", "atm", "toilet", "convenience_store"}

// foodAmenities are the OSM amenity values that mean somewhere to eat. (A
// hospital has an amenity tag too, so "has an amenity" isn't enough.)
var foodAmenities = []string{"restaurant", "fast_food", "cafe", "food_court"}

// kindSQL is the WHERE condition that picks each kind of place from the tags.
// Japanese drugstores are mostly shop=chemist, and small clinics are often
// amenity=doctors.
var kindSQL = map[string]string{
	KindFood:            `tags->>'amenity' IN ('restaurant', 'fast_food', 'cafe', 'food_court')`,
	KindShop:            `tags ? 'shop' AND tags->>'shop' NOT IN ('convenience', 'chemist')`,
	"hospital":          `tags->>'amenity' = 'hospital'`,
	"clinic":            `tags->>'amenity' IN ('clinic', 'doctors')`,
	"pharmacy":          `(tags->>'amenity' = 'pharmacy' OR tags->>'shop' = 'chemist')`,
	"police":            `tags->>'amenity' = 'police'`,
	"atm":               `tags->>'amenity' = 'atm'`,
	"toilet":            `tags->>'amenity' = 'toilets'`,
	"convenience_store": `tags->>'shop' = 'convenience'`,
}

// IsFood reports whether the place is somewhere to eat.
func (p *Place) IsFood() bool {
	return slices.Contains(foodAmenities, p.Tags["amenity"])
}

// Cuisines returns the place's cuisines; OSM separates them with ";".
func (p *Place) Cuisines() []string {
	var out []string
	for _, c := range strings.Split(p.Tags["cuisine"], ";") {
		if c = strings.TrimSpace(c); c != "" {
			out = append(out, c)
		}
	}
	return out
}

// DisplayName prefers the English name, with the Japanese one in brackets so
// it still matches the sign outside.
func (p *Place) DisplayName() string {
	if p.NameEn != "" && p.NameEn != p.Name {
		return p.NameEn + " (" + p.Name + ")"
	}
	return p.Name
}

// placeFiles are the snapshots cmd/osmfetch writes: restaurants and shops,
// and places to get help.
var placeFiles = []string{"places.json", "help.json"}

// LoadPlaces reads and checks the place snapshots from fsys (osmdata.Files in
// production). Every place needs an id, a name, a city and a location in
// Japan, and ids must be unique within a file. A place in both files (a
// convenience store with a vegan menu, say) is kept once.
func LoadPlaces(fsys fs.FS) ([]*Place, error) {
	var all []*Place
	seen := make(map[string]bool)

	for _, file := range placeFiles {
		js, err := fs.ReadFile(fsys, file)
		if err != nil {
			return nil, err
		}

		var places []*Place
		if err := json.Unmarshal(js, &places); err != nil {
			return nil, fmt.Errorf("%s: %w", file, err)
		}

		inFile := make(map[string]bool, len(places))
		for i, p := range places {
			switch {
			case p.ID == "" || p.Name == "" || p.City == "":
				return nil, fmt.Errorf("%s: place %d is missing its id, name or city", file, i)
			case p.Lat < 24 || p.Lat > 46 || p.Lng < 122 || p.Lng > 146:
				return nil, fmt.Errorf("%s: place %q is not in Japan", file, p.ID)
			case inFile[p.ID]:
				return nil, fmt.Errorf("%s: duplicate place id %q", file, p.ID)
			}
			inFile[p.ID] = true
			if seen[p.ID] {
				continue
			}
			seen[p.ID] = true
			if p.Tags == nil {
				p.Tags = map[string]string{}
			}
			all = append(all, p)
		}
	}

	return all, nil
}

// NearbyPlace is a Place plus how far it is from the search point.
type NearbyPlace struct {
	*Place
	DistanceM int `json:"distance_m"`
}

// Define a PlaceModel struct type which wraps a sql.DB connection pool.
type PlaceModel struct {
	DB *sql.DB
}

// Near returns places of one kind (KindFood, KindShop or one of HelpKinds)
// within radiusM metres of a point, nearest first.
//
// It selects a box around the point in SQL (fast, using the index), then
// works out exact distances in Go and drops the box's corners. That's plenty
// for a few thousand rows, without needing PostGIS.
func (m PlaceModel) Near(lat, lng float64, radiusM int, kind string) ([]*NearbyPlace, error) {
	condition, ok := kindSQL[kind]
	if !ok {
		return nil, fmt.Errorf("unknown kind of place %q", kind)
	}

	dLat := float64(radiusM) / 111_320
	dLng := dLat / math.Cos(lat*math.Pi/180)

	// The condition comes from kindSQL, never from the request, so adding it
	// to the query is safe.
	query := `
		SELECT id, city, name, name_en, lat, lng, tags
		FROM places
		WHERE lat BETWEEN $1 AND $2
		AND lng BETWEEN $3 AND $4
		AND ` + condition

	args := []any{lat - dLat, lat + dLat, lng - dLng, lng + dLng}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	rows, err := m.DB.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	places := []*NearbyPlace{}

	for rows.Next() {
		var p Place
		var tags []byte

		err := rows.Scan(&p.ID, &p.City, &p.Name, &p.NameEn, &p.Lat, &p.Lng, &tags)
		if err != nil {
			return nil, err
		}
		if err := json.Unmarshal(tags, &p.Tags); err != nil {
			return nil, err
		}

		d := distanceM(lat, lng, p.Lat, p.Lng)
		if d <= float64(radiusM) {
			places = append(places, &NearbyPlace{Place: &p, DistanceM: int(math.Round(d))})
		}
	}

	if err = rows.Err(); err != nil {
		return nil, err
	}

	slices.SortFunc(places, func(a, b *NearbyPlace) int { return a.DistanceM - b.DistanceM })
	return places, nil
}

// ReplaceAll swaps the whole places table for the snapshot, in one
// transaction, like EtiquetteRuleModel.ReplaceAll.
func (m PlaceModel) ReplaceAll(places []*Place) error {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	tx, err := m.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, `DELETE FROM places`); err != nil {
		return err
	}

	// A prepared statement saves re-parsing the INSERT for each of the
	// thousands of rows.
	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO places (id, city, name, name_en, lat, lng, tags)
		VALUES ($1, $2, $3, $4, $5, $6, $7)`)
	if err != nil {
		return err
	}
	defer stmt.Close()

	for _, p := range places {
		tags, err := json.Marshal(p.Tags)
		if err != nil {
			return err
		}
		if _, err := stmt.ExecContext(ctx, p.ID, p.City, p.Name, p.NameEn, p.Lat, p.Lng, tags); err != nil {
			return err
		}
	}

	return tx.Commit()
}

// distanceM is the great-circle distance between two points, in metres.
func distanceM(lat1, lng1, lat2, lng2 float64) float64 {
	const earthRadiusM = 6_371_000
	rad := math.Pi / 180

	dLat := (lat2 - lat1) * rad
	dLng := (lng2 - lng1) * rad
	h := math.Sin(dLat/2)*math.Sin(dLat/2) + math.Cos(lat1*rad)*math.Cos(lat2*rad)*math.Sin(dLng/2)*math.Sin(dLng/2)

	return 2 * earthRadiusM * math.Asin(math.Sqrt(h))
}

// PlaceFinder is the part of PlaceModel that Nearest needs, so the companion's
// tests can use a fake.
type PlaceFinder interface {
	Near(lat, lng float64, radiusM int, kind string) ([]*NearbyPlace, error)
}

// helpRadii are tried in turn until a few places turn up. Help can be
// further away than lunch: the nearest hospital may be a few kilometres off.
var helpRadii = []int{1000, 2000, 3000, 5000}

// Nearest finds up to limit places of a kind, nearest first, widening the
// search until it has at least min of them.
func Nearest(f PlaceFinder, lat, lng float64, kind string, min, limit int) ([]*NearbyPlace, error) {
	var places []*NearbyPlace
	for _, radius := range helpRadii {
		var err error
		places, err = f.Near(lat, lng, radius, kind)
		if err != nil {
			return nil, err
		}
		if len(places) >= min {
			break
		}
	}
	if len(places) > limit {
		places = places[:limit]
	}
	return places, nil
}

// PlaceSummary is what the app and the companion get for a place: the name
// in English when we have it, the Japanese on the sign, how far it is, and
// the details that matter when you need help.
type PlaceSummary struct {
	Name         string  `json:"name"`
	NameJa       string  `json:"name_ja,omitempty"`
	Kind         string  `json:"kind"`
	Lat          float64 `json:"lat"`
	Lng          float64 `json:"lng"`
	DistanceM    int     `json:"distance_m"`
	Address      string  `json:"address,omitempty"`
	OpeningHours string  `json:"opening_hours,omitempty"`
	Phone        string  `json:"phone,omitempty"`
	Website      string  `json:"website,omitempty"`
	Emergency    bool    `json:"emergency,omitempty"` // a hospital with an emergency department
	Wheelchair   string  `json:"wheelchair,omitempty"`
}

// Summarize turns a snapshot place into a PlaceSummary.
func Summarize(p *NearbyPlace, kind string) PlaceSummary {
	s := PlaceSummary{
		Name:         p.Name,
		Kind:         kind,
		Lat:          p.Lat,
		Lng:          p.Lng,
		DistanceM:    p.DistanceM,
		OpeningHours: p.Tags["opening_hours"],
		Phone:        p.Tags["phone"],
		Website:      p.Tags["website"],
		Emergency:    p.Tags["emergency"] == "yes",
		Wheelchair:   p.Tags["wheelchair"],
	}
	if p.NameEn != "" && p.NameEn != p.Name {
		s.Name, s.NameJa = p.NameEn, p.Name
	}
	return s
}

// DistanceM is the distance between two points in metres, for places that
// don't come from the snapshot.
func DistanceM(lat1, lng1, lat2, lng2 float64) int {
	return int(math.Round(distanceM(lat1, lng1, lat2, lng2)))
}

// LoadStations reads stations.json, the stations with their English names,
// for labelling stations the timetables only name in Japanese. It's optional:
// without the file, stations keep their Japanese names.
func LoadStations(fsys fs.FS) ([]*Place, error) {
	js, err := fs.ReadFile(fsys, "stations.json")
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	var stations []*Place
	if err := json.Unmarshal(js, &stations); err != nil {
		return nil, fmt.Errorf("stations.json: %w", err)
	}
	return stations, nil
}
