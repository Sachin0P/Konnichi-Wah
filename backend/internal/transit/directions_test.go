package transit

import (
	"math"
	"slices"
	"testing"
)

func TestDecodePath(t *testing.T) {
	// The example from Google's polyline documentation, at precision 5.
	got := decodePath("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
	want := [][2]float64{{38.5, -120.2}, {40.7, -120.95}, {43.252, -126.453}}

	if len(got) != len(want) {
		t.Fatalf("got %v", got)
	}
	for i := range want {
		if math.Abs(got[i][0]-want[i][0]) > 1e-9 || math.Abs(got[i][1]-want[i][1]) > 1e-9 {
			t.Errorf("point %d = %v; want %v", i, got[i], want[i])
		}
	}
}

func TestSplitName(t *testing.T) {
	tests := []struct{ in, en, ja string }{
		{"千駄ケ谷 Sendagaya", "Sendagaya", "千駄ケ谷"},
		{"京都", "京都", ""},
		{"Tokyo Station", "Tokyo Station", ""},
	}
	for _, tt := range tests {
		if en, ja := splitName(tt.in); en != tt.en || ja != tt.ja {
			t.Errorf("splitName(%q) = %q, %q", tt.in, en, ja)
		}
	}
}

func TestTowards(t *testing.T) {
	if got := towards("(普通 Local) 津田沼 Tsudanuma"); got != "Tsudanuma" {
		t.Errorf("got %q", got)
	}
}

func TestExitName(t *testing.T) {
	// Real path names from a walk out of Asakusa station.
	out := []string{"", "つくばエクスプレス浅草駅南自転車駐車場", "つくばエクスプレス浅草駅;A1出入口", "六区ブロードウェイ"}
	if got := exitName(out, true); got != "A1" {
		t.Errorf("exit = %q; want A1", got)
	}

	in := []string{"京都駅;中央口", "南北自由通路", "京都駅;八条口"}
	if got := exitName(in, false); got != "八条口" {
		t.Errorf("exit = %q; want the last one, 八条口", got)
	}

	// A ticket gate (改札口) isn't an exit.
	if got := exitName([]string{"渋谷駅;中央改札口"}, true); got != "" {
		t.Errorf("exit = %q; want none", got)
	}
}

func TestPlatform(t *testing.T) {
	steps := []string{"JR秋葉原駅", "JR秋葉原駅", "JR秋葉原駅;2階;1番線"}
	if got := platform(steps); got != "1" {
		t.Errorf("platform = %q; want 1", got)
	}

	// A platform early in the walk is where you arrived, not where you're going.
	early := []string{"JR秋葉原駅;2階;3番線", "a", "b", "c", "d"}
	if got := platform(early); got != "" {
		t.Errorf("platform = %q; want none", got)
	}
}

func TestViaNames(t *testing.T) {
	got := viaNames([]string{"", "つくばエクスプレス浅草駅;A1出入口", "六区ブロードウェイ", "六区ブロードウェイ", "奥山おまいりまち"})
	if !slices.Equal(got, []string{"六区ブロードウェイ", "奥山おまいりまち"}) {
		t.Errorf("got %v", got)
	}
}

func TestJourneyLine(t *testing.T) {
	tests := []struct{ short, long, en, ja string }{
		{"JB", "JR中央・総武緩行線 JR Chūō-Sobu Local Line", "JR Chūō-Sobu Local Line", "JR中央・総武緩行線"},
		{"A", "浅草線", "Toei Asakusa Line", "浅草線"},
		{"みやこ路快速", "", "Miyakoji Rapid", ""},
		{"みやこ路快速601号", "", "Miyakoji Rapid", ""},
	}
	for _, tt := range tests {
		if en, ja := journeyLine(tt.short, tt.long); en != tt.en || ja != tt.ja {
			t.Errorf("journeyLine(%q, %q) = %q, %q; want %q, %q", tt.short, tt.long, en, ja, tt.en, tt.ja)
		}
	}
}

func TestDecodePathIsNeverNil(t *testing.T) {
	if got := decodePath("", 7); got == nil {
		t.Error("decodePath returned nil; want an empty path")
	}
}

func TestStationNames(t *testing.T) {
	names := NewStationNames([]Station{
		{Name: "新宿三丁目", NameEn: "Shinjuku-sanchome", Lat: 35.6906, Lng: 139.7048},
		{Name: "日本橋", NameEn: "Nihombashi", Lat: 35.6820, Lng: 139.7745},
		{Name: "日本橋", NameEn: "Nippombashi", Lat: 34.6667, Lng: 135.5063}, // Osaka's
	})

	if got := names.English("新宿三丁目", 35.6905, 139.7050); got != "Shinjuku-sanchome" {
		t.Errorf("got %q", got)
	}
	// The nearest one with that name, so Osaka's 日本橋 isn't Tokyo's.
	if got := names.English("日本橋駅", 34.6668, 135.5060); got != "Nippombashi" {
		t.Errorf("got %q; want Osaka's Nippombashi", got)
	}
	if got := names.English("浅草", 35.71, 139.79); got != "" {
		t.Errorf("got %q for an unknown station", got)
	}
	var none *StationNames
	if got := none.English("新宿三丁目", 35.69, 139.70); got != "" {
		t.Errorf("a nil StationNames returned %q", got)
	}
}
