package desimatch

import (
	"strings"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// place builds a restaurant; a "name" entry in tags sets its name.
func place(tags map[string]string) *data.Place {
	tags["amenity"] = "restaurant"
	name := tags["name"]
	if name == "" {
		name = "Test"
	}
	delete(tags, "name")
	return &data.Place{Name: name, Tags: tags}
}

func TestScore(t *testing.T) {
	tests := []struct {
		name        string
		tags        map[string]string
		diet        string
		avoid       []string
		want        int
		wantCaution string // substring, or "" for no caution
	}{
		{name: "Veg at a vegan-only place", tags: map[string]string{"diet:vegan": "only", "cuisine": "japanese"}, diet: "veg", want: 5},
		{name: "Veg at an Indian restaurant", tags: map[string]string{"cuisine": "indian"}, diet: "veg", want: 4},
		// Tagged only vegan=yes, but the name says it's a vegan restaurant.
		{name: "Veg at a place called Vegan Dining", tags: map[string]string{"diet:vegan": "yes", "cuisine": "japanese", "name": "Marugoto Vegan Dining Asakusa"}, diet: "veg", want: 5},
		// A ramen shop with "vegan" in its name has vegan options, not a
		// vegan kitchen.
		{name: "Veg at a ramen shop that mentions a vegan menu", tags: map[string]string{"cuisine": "ramen", "name": "Ramen Toriton (ask for vegan menu)"}, diet: "veg", want: 3, wantCaution: "dashi"},
		{name: "Veg at a ramen shop with veg options", tags: map[string]string{"cuisine": "ramen", "diet:vegetarian": "yes"}, diet: "veg", want: 3, wantCaution: "dashi"},
		{name: "Veg at a plain sushi bar", tags: map[string]string{"cuisine": "sushi"}, diet: "veg", want: 1, wantCaution: "dashi"},
		{name: "Veg at CoCo Ichibanya", tags: map[string]string{"cuisine": "japanese_curry", "brand": "CoCo壱番屋"}, diet: "veg", want: 2, wantCaution: "vegetarian curry"},
		{name: "Jain at a vegetarian place", tags: map[string]string{"diet:vegetarian": "only"}, diet: "jain", want: 4, wantCaution: "onion"},
		{name: "Jain at a sushi bar", tags: map[string]string{"cuisine": "sushi"}, diet: "jain", want: 1, wantCaution: "onion"},
		{name: "Halal at a halal place", tags: map[string]string{"diet:halal": "yes", "cuisine": "indian"}, diet: "halal", want: 5},
		{name: "Halal at an Indian place", tags: map[string]string{"cuisine": "nepalese;indian"}, diet: "halal", want: 3, wantCaution: "halal"},
		{name: "Non-veg at an Indian place", tags: map[string]string{"cuisine": "indian"}, diet: "non_veg", want: 5},
		{name: "Non-veg avoiding pork at a ramen shop", tags: map[string]string{"cuisine": "ramen"}, diet: "non_veg", avoid: []string{"pork"}, want: 1, wantCaution: "pork"},
		{name: "Non-veg avoiding seafood at a sushi bar", tags: map[string]string{"cuisine": "sushi"}, diet: "non_veg", avoid: []string{"seafood"}, want: 1, wantCaution: "seafood"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := Score(place(tt.tags), data.UserProfile{Diet: tt.diet, Avoid: tt.avoid})

			if m.Score != tt.want {
				t.Errorf("score = %d; want %d (reasons %v)", m.Score, tt.want, m.Reasons)
			}
			if tt.wantCaution == "" && m.Caution != "" {
				t.Errorf("unexpected caution %q", m.Caution)
			}
			if tt.wantCaution != "" && !strings.Contains(m.Caution, tt.wantCaution) {
				t.Errorf("caution = %q; want it to mention %q", m.Caution, tt.wantCaution)
			}
		})
	}
}

func TestScoreExplainsItself(t *testing.T) {
	m := Score(place(map[string]string{"cuisine": "indian", "diet:vegetarian": "yes"}), data.UserProfile{Diet: "veg", SpiceLevel: "indian_spicy"})

	for _, want := range []string{"Has vegetarian options", "Indian or Nepali food", "Can make it properly spicy"} {
		found := false
		for _, r := range m.Reasons {
			found = found || r == want
		}
		if !found {
			t.Errorf("reasons %v missing %q", m.Reasons, want)
		}
	}
}

// Every reason and caution needs a Hindi version, or a Hindi-speaking
// traveller would see a stray English line.
func TestEveryTextHasHindi(t *testing.T) {
	for _, s := range allTexts {
		if hindi[s] == "" {
			t.Errorf("no Hindi for %q", s)
		}
	}
}

func TestMatchInHindi(t *testing.T) {
	m := Score(place(map[string]string{"cuisine": "indian"}), data.UserProfile{Diet: "jain"})

	hi := m.In("hi")
	if hi.Score != m.Score || hi.Reasons[0] != "भारतीय या नेपाली खाना" || !strings.Contains(hi.Caution, "जैन") {
		t.Errorf("In(hi) = %+v", hi)
	}
	if en := m.In("en"); en.Caution != m.Caution {
		t.Errorf("In(en) changed the caution to %q", en.Caution)
	}
}
