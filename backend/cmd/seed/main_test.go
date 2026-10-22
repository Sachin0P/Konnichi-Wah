package main

import (
	"testing"
	"testing/fstest"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"
)

// TestRulebook checks the real etiquette.json, so that a broken edit is caught
// by `make audit` before anyone tries to seed it.
func TestRulebook(t *testing.T) {
	rules, err := loadRules(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}

	// Every place type must have at least one rule, otherwise stops of that
	// type would show no culture tips at all.
	counts := make(map[string]int)
	for _, rule := range rules {
		for _, placeType := range rule.PlaceTypes {
			counts[placeType]++
		}
	}

	for _, placeType := range data.PlaceTypes {
		if counts[placeType] == 0 {
			t.Errorf("no rules for place type %q", placeType)
		}
	}
}

func TestLoadRulesRejectsBadInput(t *testing.T) {
	tests := []struct {
		name string
		json string
	}{
		{
			name: "Unknown key",
			json: `[{"id": "a", "place_types": ["temple"], "title": "T", "tip": "T", "severity": "important", "titel": "x"}]`,
		},
		{
			name: "Unknown place type",
			json: `[{"id": "a", "place_types": ["castle"], "title": "T", "tip": "T", "severity": "important"}]`,
		},
		{
			name: "Bad severity",
			json: `[{"id": "a", "place_types": ["temple"], "title": "T", "tip": "T", "severity": "urgent"}]`,
		},
		{
			name: "Bad id",
			json: `[{"id": "Temple Shoes", "place_types": ["temple"], "title": "T", "tip": "T", "severity": "important"}]`,
		},
		{
			name: "Duplicate id",
			json: `[{"id": "a", "place_types": ["temple"], "title": "T", "tip": "T", "severity": "important"},
			        {"id": "a", "place_types": ["shrine"], "title": "T", "tip": "T", "severity": "helpful"}]`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fsys := fstest.MapFS{"etiquette.json": {Data: []byte(tt.json)}}

			_, err := loadRules(fsys)
			if err == nil {
				t.Error("expected an error, got nil")
			}
		})
	}
}
