package data

import (
	"encoding/json"
	"io/fs"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"
)

// TestHindiCoversEverything checks the real Hindi files: they must load, and
// every etiquette rule and diet card needs a Hindi version (and nothing
// else), so a Hindi-speaking traveller never sees a stray English tip.
func TestHindiCoversEverything(t *testing.T) {
	h, err := LoadHindi(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}

	js, err := fs.ReadFile(rulebook.Files, "etiquette.json")
	if err != nil {
		t.Fatal(err)
	}
	var rules []EtiquetteRule
	if err := json.Unmarshal(js, &rules); err != nil {
		t.Fatal(err)
	}

	ruleIDs := make(map[string]bool)
	for _, r := range rules {
		ruleIDs[r.ID] = true
		if _, ok := h.Rules[r.ID]; !ok {
			t.Errorf("etiquette.hi.json: no Hindi for rule %q", r.ID)
		}
	}
	for id := range h.Rules {
		if !ruleIDs[id] {
			t.Errorf("etiquette.hi.json: %q isn't a rule in etiquette.json", id)
		}
	}

	cards, err := LoadPhraseCards(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}

	cardIDs := make(map[string]bool)
	for _, c := range cards {
		cardIDs[c.ID] = true
		if _, ok := h.Cards[c.ID]; !ok {
			t.Errorf("phrases.hi.json: no Hindi for card %q", c.ID)
		}
	}
	for id := range h.Cards {
		if !cardIDs[id] {
			t.Errorf("phrases.hi.json: %q isn't a card in phrases.json", id)
		}
	}
}

func TestHindiFallsBackToEnglish(t *testing.T) {
	h := &Hindi{Rules: map[string]HindiRule{"temple-shoes": {Title: "जूते उतारें", Tip: "जूते उतार दें।"}}}

	shoes := &EtiquetteRule{ID: "temple-shoes", Title: "Remove your shoes", Tip: "Take them off."}
	other := &EtiquetteRule{ID: "temple-quiet", Title: "Keep your voice low", Tip: "Talk quietly."}

	if title, _ := h.Rule(shoes, "hi"); title != "जूते उतारें" {
		t.Errorf("hi title = %q", title)
	}
	if title, _ := h.Rule(shoes, "en"); title != "Remove your shoes" {
		t.Errorf("en title = %q", title)
	}
	if title, _ := h.Rule(other, "hi"); title != "Keep your voice low" {
		t.Errorf("untranslated rule title = %q; want the English", title)
	}
}
