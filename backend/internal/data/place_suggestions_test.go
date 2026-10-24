package data

import "testing"

func TestSuggestStations(t *testing.T) {
	stations := []*Place{
		{Name: "上野広小路", NameEn: "Ueno-hirokoji", City: "Tokyo", Lat: 35.7077, Lng: 139.7731},
		{Name: "上野", NameEn: "Ueno", City: "Tokyo", Lat: 35.7138, Lng: 139.7770},
		{Name: "上野", NameEn: "Ueno", City: "Tokyo", Lat: 35.7112, Lng: 139.7774}, // the Tokyo Metro platforms
		{Name: "新宿三丁目", NameEn: "Shinjuku-sanchome", City: "Tokyo", Lat: 35.6906, Lng: 139.7063},
		{Name: "新宿", NameEn: "Shinjuku", City: "Tokyo", Lat: 35.6896, Lng: 139.7006},
		{Name: "大阪", NameEn: "Ōsaka", City: "Osaka", Lat: 34.7025, Lng: 135.4959},
		{Name: "京都", NameEn: "Kyoto", City: "Kyoto", Lat: 34.9858, Lng: 135.7588},
	}

	tests := []struct {
		name  string
		query string
		want  []string
	}{
		{"exact match first, duplicates merged", "ueno", []string{"Ueno", "Ueno-hirokoji"}},
		{"prefix", "shinj", []string{"Shinjuku", "Shinjuku-sanchome"}},
		{"typed with a space and the word station", "Shinjuku Sanchome Station", []string{"Shinjuku-sanchome"}},
		{"a later word", "sanchome", []string{"Shinjuku-sanchome"}},
		{"long vowels typed plainly", "osaka", []string{"Ōsaka"}},
		{"Japanese", "新宿", []string{"Shinjuku", "Shinjuku-sanchome"}},
		{"far away, but exactly what was typed", "kyoto", []string{"Kyoto"}},
		{"far away and only the start of the name", "kyo", nil},
		{"no match", "zzz", nil},
		{"too short to match inside a name", "y", nil},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := SuggestStations(stations, tt.query, 35.69, 139.70, 5)
			if len(got) != len(tt.want) {
				t.Fatalf("got %v, want %v", got, tt.want)
			}
			for i, s := range got {
				if s.Name != tt.want[i] || s.Kind != "station" {
					t.Errorf("result %d = %+v, want %s", i, s, tt.want[i])
				}
			}
		})
	}
}
