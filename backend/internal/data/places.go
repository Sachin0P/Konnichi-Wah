package data

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"io/fs"
	"math"
	"slices"
	"strings"
	"time"
)

// Place is a restaurant or shop from OpenStreetMap. Tags holds the few OSM
// tags we keep, e.g. "cuisine": "indian", "diet:vegetarian": "only".
type Place struct {
	ID     string            `json:"id"`
	City   string            `json:"city"`
	Name   string            `json:"name"`
	NameEn string            `json:"name_en,omitempty"`
	Lat    float64           `json:"lat"`
	Lng    float64           `json:"lng"`
	Tags   map[string]string `json:"tags"`
}

// IsFood reports whether the place is somewhere to eat.
func (p *Place) IsFood() bool {
	return p.Tags["amenity"] != ""
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

// LoadPlaces reads and checks places.json from fsys (osmdata.Files in
// production). Every place needs an id, a name, a city and a location in
// Japan, and ids must be unique.
func LoadPlaces(fsys fs.FS) ([]*Place, error) {
	js, err := fs.ReadFile(fsys, "places.json")
	if err != nil {
		return nil, err
	}

	var places []*Place
	if err := json.Unmarshal(js, &places); err != nil {
		return nil, fmt.Errorf("places.json: %w", err)
	}

	seen := make(map[string]bool, len(places))
	for i, p := range places {
		switch {
		case p.ID == "" || p.Name == "" || p.City == "":
			return nil, fmt.Errorf("places.json: place %d is missing its id, name or city", i)
		case p.Lat < 24 || p.Lat > 46 || p.Lng < 122 || p.Lng > 146:
			return nil, fmt.Errorf("places.json: place %q is not in Japan", p.ID)
		case seen[p.ID]:
			return nil, fmt.Errorf("places.json: duplicate place id %q", p.ID)
		}
		seen[p.ID] = true
		if p.Tags == nil {
			p.Tags = map[string]string{}
		}
	}

	return places, nil
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

// Near returns places within radiusM metres of a point, nearest first. If
// food is true it returns only places to eat, otherwise only shops.
//
// It selects a box around the point in SQL (fast, using the index), then
// works out exact distances in Go and drops the box's corners. That's plenty
// for a few thousand rows, without needing PostGIS.
func (m PlaceModel) Near(lat, lng float64, radiusM int, food bool) ([]*NearbyPlace, error) {
	dLat := float64(radiusM) / 111_320
	dLng := dLat / math.Cos(lat*math.Pi/180)

	query := `
		SELECT id, city, name, name_en, lat, lng, tags
		FROM places
		WHERE lat BETWEEN $1 AND $2
		AND lng BETWEEN $3 AND $4
		AND (tags ? 'amenity') = $5`

	args := []any{lat - dLat, lat + dLat, lng - dLng, lng + dLng, food}

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
