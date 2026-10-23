package planner

import (
	"fmt"
	"slices"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// counter returns an id generator that yields "st1", "st2", ...
func counter() func() string {
	n := 0
	return func() string {
		n++
		return fmt.Sprintf("st%d", n)
	}
}

func sight(name, city string) data.Suggestion {
	return data.Suggestion{ID: name, Kind: "place", Name: name, City: city, PlaceType: "temple", Reason: "r"}
}

func meal(name, city string) data.Suggestion {
	return data.Suggestion{ID: name, Kind: "food", Name: name, City: city, PlaceType: "restaurant", Reason: "r"}
}

// stopNames returns each day's stop names, for easy comparison.
func stopNames(days []data.Day) [][]string {
	var out [][]string
	for _, d := range days {
		var names []string
		for _, s := range d.Stops {
			names = append(names, s.PlaceName)
		}
		out = append(out, names)
	}
	return out
}

func TestArrangeKeepsEverySuggestionOnce(t *testing.T) {
	cities := []data.CityPlan{{Name: "Tokyo", Days: 3}, {Name: "Kyoto", Days: 2}}
	suggestions := []data.Suggestion{
		sight("Senso-ji", "Tokyo"), sight("Meiji Jingu", "Tokyo"), sight("Ueno", "Tokyo"), sight("Akihabara", "Tokyo"),
		sight("Fushimi Inari", "Kyoto"), sight("Todai-ji", "Nara"), sight("Kinkaku-ji", "Kyoto"),
		meal("T's Tantan", "Tokyo"), meal("Nirvanam", "Tokyo"), meal("Shigetsu", "Kyoto"),
		sight("Somewhere odd", "Atlantis"),
	}

	days := Arrange(cities, suggestions, counter())

	if len(days) != 5 {
		t.Fatalf("got %d days; want 5", len(days))
	}

	var seen []string
	var ids []string
	for i, d := range days {
		if d.Index != i+1 {
			t.Errorf("day %d has index %d", i, d.Index)
		}
		for _, s := range d.Stops {
			seen = append(seen, s.PlaceName)
			ids = append(ids, s.ID)
		}
	}

	for _, s := range suggestions {
		if n := countOf(seen, s.Name); n != 1 {
			t.Errorf("%q appears %d times; want 1", s.Name, n)
		}
	}

	slices.Sort(ids)
	if len(slices.Compact(ids)) != len(seen) {
		t.Error("stop ids are not unique")
	}

	// An unrecognised town goes to the city with the most days (Tokyo).
	if !dayHasStop(days[:3], "Somewhere odd") {
		t.Error("unrecognised town was not placed in Tokyo")
	}
}

func TestArrangeDayTripGetsItsOwnDay(t *testing.T) {
	cities := []data.CityPlan{{Name: "Kyoto", Days: 2}}
	suggestions := []data.Suggestion{
		sight("Todai-ji", "Nara"), sight("Fushimi Inari", "Kyoto"),
		sight("Nara Park", "Nara"), sight("Kinkaku-ji", "Kyoto"),
	}

	got := stopNames(Arrange(cities, suggestions, counter()))
	want := [][]string{{"Fushimi Inari", "Kinkaku-ji"}, {"Todai-ji", "Nara Park"}}

	if !slices.EqualFunc(got, want, slices.Equal) {
		t.Errorf("got %v; want %v", got, want)
	}
}

func TestArrangeDayOrderAndSlots(t *testing.T) {
	gion := data.Suggestion{ID: "gion", Kind: "activity", Name: "Gion walk", City: "Kyoto", PlaceType: "street", Reason: "r"}

	tests := []struct {
		name        string
		suggestions []data.Suggestion
		want        []string
	}{
		{
			// Temples close at dusk, so a third temple goes in the afternoon,
			// not the evening.
			name: "Daytime sights",
			suggestions: []data.Suggestion{
				sight("A", "Kyoto"), sight("B", "Kyoto"), sight("C", "Kyoto"),
				meal("Lunch place", "Kyoto"), meal("Dinner place", "Kyoto"),
			},
			want: []string{"morning:A", "lunch:Lunch place", "afternoon:B", "afternoon:C", "dinner:Dinner place"},
		},
		{
			// A street walk moves to the evening even if it was listed first.
			name:        "Evening street",
			suggestions: []data.Suggestion{gion, sight("A", "Kyoto"), meal("Lunch place", "Kyoto")},
			want:        []string{"morning:A", "lunch:Lunch place", "evening:Gion walk"},
		},
		{
			name:        "Only evening sights",
			suggestions: []data.Suggestion{gion, {ID: "x", Kind: "place", Name: "Pontocho", City: "Kyoto", PlaceType: "street", Reason: "r"}},
			want:        []string{"morning:Gion walk", "evening:Pontocho"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			days := Arrange([]data.CityPlan{{Name: "Kyoto", Days: 1}}, tt.suggestions, counter())

			var got []string
			for _, s := range days[0].Stops {
				got = append(got, s.TimeSlot+":"+s.PlaceName)
			}

			if !slices.Equal(got, tt.want) {
				t.Errorf("got %v; want %v", got, tt.want)
			}
		})
	}
}

func TestArrangeSpreadsEvenly(t *testing.T) {
	tests := []struct {
		name   string
		sights int
		days   int
		want   []int // stops per day
	}{
		{name: "Exact fit", sights: 8, days: 4, want: []int{2, 2, 2, 2}},
		{name: "Uneven", sights: 7, days: 3, want: []int{3, 2, 2}},
		{name: "One extra", sights: 4, days: 3, want: []int{2, 1, 1}},
		{name: "Fewer sights than days", sights: 2, days: 4, want: []int{1, 1, 0, 0}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var suggestions []data.Suggestion
			for i := range tt.sights {
				suggestions = append(suggestions, sight(fmt.Sprintf("S%d", i), "Tokyo"))
			}

			var got []int
			for _, d := range Arrange([]data.CityPlan{{Name: "Tokyo", Days: tt.days}}, suggestions, counter()) {
				got = append(got, len(d.Stops))
			}
			if !slices.Equal(got, tt.want) {
				t.Errorf("got %v; want %v", got, tt.want)
			}
		})
	}
}

func countOf(values []string, target string) int {
	n := 0
	for _, v := range values {
		if v == target {
			n++
		}
	}
	return n
}

func dayHasStop(days []data.Day, name string) bool {
	for _, d := range days {
		for _, s := range d.Stops {
			if s.PlaceName == name {
				return true
			}
		}
	}
	return false
}
