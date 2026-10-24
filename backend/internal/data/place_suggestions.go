package data

import (
	"sort"
	"strings"
	"unicode"
)

// PlaceSuggestion is one entry in the search-as-you-type dropdown: a place the
// traveller can pick, with exact coordinates, so a route never depends on
// guessing what they meant by "shinjuku".
type PlaceSuggestion struct {
	Name   string  `json:"name"`
	NameJa string  `json:"name_ja,omitempty"`
	Kind   string  `json:"kind"`             // "station", or an OpenStreetMap tag value such as "place_of_worship"
	Detail string  `json:"detail,omitempty"` // the city or address, to tell same-named places apart
	Lat    float64 `json:"lat"`
	Lng    float64 `json:"lng"`
}

// SuggestStations finds the stations whose English or Japanese name starts
// with (or, for longer input, contains) what the traveller has typed so far.
// Exact and prefix matches come first, then the nearest to lat, lng, so
// "ueno" in Tokyo gives Ueno before Ueno-hirokoji, and Kyoto's stations
// before Osaka's.
func SuggestStations(stations []*Place, query string, lat, lng float64, limit int) []PlaceSuggestion {
	q := foldName(query)
	q = strings.TrimSuffix(strings.TrimSuffix(q, "station"), "駅")
	if q == "" {
		return []PlaceSuggestion{}
	}

	type match struct {
		place *Place
		score int // lower is better
		size  int // the name's length: "shinj" means Shinjuku before Shinjuku-gyoemmae
		km    float64
	}
	var matches []match
	for _, p := range stations {
		score := -1
		for _, name := range []string{p.NameEn, p.Name} {
			if s := matchScore(name, q); s >= 0 && (score < 0 || s < score) {
				score = s
			}
		}
		if score < 0 {
			continue
		}
		// Higashi-Fushimi in Tokyo is no use to someone typing "fushimi" in
		// Kyoto. A station further than 50 km away is only suggested when
		// its name is exactly what was typed ("nara", from Kyoto).
		km := distanceM(lat, lng, p.Lat, p.Lng) / 1000
		if km > 50 && score > 0 {
			continue
		}
		matches = append(matches, match{p, score, len(foldName(p.NameEn)), km})
	}

	sort.Slice(matches, func(i, j int) bool {
		if matches[i].score != matches[j].score {
			return matches[i].score < matches[j].score
		}
		if matches[i].size != matches[j].size {
			return matches[i].size < matches[j].size
		}
		return matches[i].km < matches[j].km
	})

	// A big station appears several times in OpenStreetMap, once per
	// operator (JR, Tokyo Metro, Toei...). Show it once: skip a station with
	// the same name as one already listed within 1.5 km.
	out := []PlaceSuggestion{}
	for _, m := range matches {
		if len(out) == limit {
			break
		}
		name := m.place.NameEn
		if name == "" {
			name = m.place.Name
		}
		duplicate := false
		for _, s := range out {
			if s.Name == name && distanceM(s.Lat, s.Lng, m.place.Lat, m.place.Lng) < 1500 {
				duplicate = true
				break
			}
		}
		if duplicate {
			continue
		}
		out = append(out, PlaceSuggestion{
			Name: name, NameJa: m.place.Name, Kind: "station", Detail: m.place.City,
			Lat: m.place.Lat, Lng: m.place.Lng,
		})
	}
	return out
}

// NameMatches reports whether a name matches a half-typed query the way
// station suggestions do: the whole name, the start of a word in it, or (for
// three or more characters) anywhere in it. Photon's own matching is fuzzy,
// so "sensoj" also finds a hairdresser called SEASON; this weeds those out.
func NameMatches(name, query string) bool {
	return matchScore(name, foldName(query)) >= 0
}

// matchScore says how well a station name matches the folded query: 0 for
// the whole name, 1 for its start, 2 for the start of a later word
// ("sanchome" in Shinjuku-sanchome), 3 for anywhere in it; -1 for no match.
// Matching anywhere needs three characters, or "e" would match half the list.
func matchScore(name, q string) int {
	if name == "" {
		return -1
	}
	full := foldName(name)
	switch {
	case full == q:
		return 0
	case strings.HasPrefix(full, q):
		return 1
	}
	for _, word := range strings.FieldsFunc(strings.ToLower(name), func(r rune) bool { return r == ' ' || r == '-' }) {
		if strings.HasPrefix(foldName(word), q) {
			return 2
		}
	}
	if len([]rune(q)) >= 3 && strings.Contains(full, q) {
		return 3
	}
	return -1
}

// foldName makes names comparable however they're typed: lower case, with
// the long-vowel marks removed (Ōsaka, Sensō-ji) and without spaces, hyphens
// or apostrophes, so "shinjuku sanchome" matches "Shinjuku-sanchome".
// Japanese characters are kept as they are.
func foldName(s string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(s) {
		switch r {
		case 'ā', 'â':
			r = 'a'
		case 'ē', 'ê':
			r = 'e'
		case 'ī', 'î':
			r = 'i'
		case 'ō', 'ô':
			r = 'o'
		case 'ū', 'û':
			r = 'u'
		}
		if unicode.IsLetter(r) || unicode.IsDigit(r) {
			b.WriteRune(r)
		}
	}
	return b.String()
}
