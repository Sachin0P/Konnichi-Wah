package data

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/fs"
	"unicode"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// PhraseCard is one hand-checked Japanese card from rulebook/phrases.json.
// Exactly one of Diet, Avoid or Ask says what it's for: a whole diet ("veg"),
// one thing to avoid ("pork"), or a question ("dashi").
type PhraseCard struct {
	ID       string `json:"id"`
	Diet     string `json:"diet,omitempty"`
	Avoid    string `json:"avoid,omitempty"`
	Ask      string `json:"ask,omitempty"`
	English  string `json:"english"`
	Japanese string `json:"japanese"`
	Romaji   string `json:"romaji"`
}

// Phrase returns the card in the shape the companion's replies use.
func (p PhraseCard) Phrase() Phrase {
	return Phrase{English: p.English, Japanese: p.Japanese, Romaji: p.Romaji}
}

func ValidatePhraseCard(v *validator.Validator, p *PhraseCard) {
	v.Check(p.ID != "" && validator.Matches(p.ID, RuleIDRX), "id", "must be lowercase words separated by hyphens")

	purposes := 0
	for _, s := range []string{p.Diet, p.Avoid, p.Ask} {
		if s != "" {
			purposes++
		}
	}
	v.Check(purposes == 1, "purpose", "exactly one of diet, avoid or ask must be set")

	if p.Diet != "" {
		v.Check(validator.PermittedValue(p.Diet, Diets...), "diet", mustBeOneOf(Diets))
	}
	if p.Avoid != "" {
		v.Check(validator.PermittedValue(p.Avoid, AvoidItems...), "avoid", mustBeOneOf(AvoidItems))
	}

	v.Check(p.English != "", "english", "must be provided")
	v.Check(p.Romaji != "", "romaji", "must be provided")
	v.Check(hasJapanese(p.Japanese), "japanese", "must be written in Japanese script")
}

// LoadPhraseCards reads and checks phrases.json from fsys (rulebook.Files in
// production).
func LoadPhraseCards(fsys fs.FS) ([]PhraseCard, error) {
	js, err := fs.ReadFile(fsys, "phrases.json")
	if err != nil {
		return nil, err
	}

	dec := json.NewDecoder(bytes.NewReader(js))
	dec.DisallowUnknownFields()

	var cards []PhraseCard
	if err := dec.Decode(&cards); err != nil {
		return nil, fmt.Errorf("phrases.json: %w", err)
	}

	seen := make(map[string]bool)
	for i := range cards {
		v := validator.New()
		if ValidatePhraseCard(v, &cards[i]); !v.Valid() {
			return nil, fmt.Errorf("phrases.json: card %d (%q) is invalid: %v", i, cards[i].ID, v.Errors)
		}
		if seen[cards[i].ID] {
			return nil, fmt.Errorf("phrases.json: duplicate card id %q", cards[i].ID)
		}
		seen[cards[i].ID] = true
	}

	return cards, nil
}

func hasJapanese(s string) bool {
	for _, r := range s {
		if unicode.In(r, unicode.Hiragana, unicode.Katakana, unicode.Han) {
			return true
		}
	}
	return false
}
