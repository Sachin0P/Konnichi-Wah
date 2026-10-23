package data

import (
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"
)

// TestPhraseCards checks the real phrases.json: it must load, and every diet
// (except non_veg, which has nothing to explain) and every avoid item needs a
// card, so the companion never falls back to machine translation for them.
func TestPhraseCards(t *testing.T) {
	cards, err := LoadPhraseCards(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}

	diets := make(map[string]bool)
	avoids := make(map[string]bool)
	for _, c := range cards {
		diets[c.Diet] = true
		avoids[c.Avoid] = true
	}

	for _, d := range Diets {
		if d != "non_veg" && !diets[d] {
			t.Errorf("no card for diet %q", d)
		}
	}
	for _, a := range AvoidItems {
		if !avoids[a] {
			t.Errorf("no card for avoid item %q", a)
		}
	}
}
