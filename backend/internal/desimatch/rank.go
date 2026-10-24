package desimatch

import (
	"cmp"
	"slices"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// Finder is the part of data.PlaceModel that Search needs.
type Finder interface {
	Near(lat, lng float64, radiusM int, food bool) ([]*data.NearbyPlace, error)
}

// searchRadii are tried in turn until enough good restaurants turn up: a
// 10-minute walk first, then further. Around a big park like Nara Park, the
// restaurants are all 1-2 km away by the stations.
var searchRadii = []int{1000, 2000, 3000}

// Search finds and ranks restaurants near a point, widening the search until
// it has at least three results (or reaches 3 km).
func Search(f Finder, lat, lng float64, profile data.UserProfile, limit int) ([]Result, error) {
	var results []Result
	for _, radius := range searchRadii {
		places, err := f.Near(lat, lng, radius, true)
		if err != nil {
			return nil, err
		}
		results = Rank(places, profile, limit)
		if len(results) >= 3 {
			break
		}
	}
	return results, nil
}

// Result is a restaurant with its Desi Match for one traveller.
type Result struct {
	*data.NearbyPlace
	Match Match `json:"desi_match"`
}

// Rank scores restaurants for a traveller and returns the best ones: places
// scoring 1 (a poor fit) are left out, and the rest are sorted by score, then
// by distance. Both the /v1/food endpoint and the companion use this, so they
// always agree.
func Rank(places []*data.NearbyPlace, profile data.UserProfile, limit int) []Result {
	results := []Result{}
	for _, p := range places {
		if !p.IsFood() {
			continue
		}
		m := Score(p.Place, profile)
		if m.Score >= 2 {
			results = append(results, Result{NearbyPlace: p, Match: m})
		}
	}

	slices.SortStableFunc(results, func(a, b Result) int {
		return cmp.Or(b.Match.Score-a.Match.Score, a.DistanceM-b.DistanceM)
	})

	return results[:min(limit, len(results))]
}
