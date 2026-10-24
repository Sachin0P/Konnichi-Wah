package data

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"unicode"
)

// Languages lists the languages the app can be used in. English is the
// default everywhere a language is optional.
var Languages = []string{"en", "hi"}

// HindiRule is the Hindi version of one etiquette rule.
type HindiRule struct {
	Title string `json:"title"`
	Tip   string `json:"tip"`
}

// Hindi holds the Hindi versions of our hand-written content, keyed by id:
// the etiquette rules (rulebook/etiquette.hi.json) and the meaning of each
// diet card (rulebook/phrases.hi.json). The Japanese on the cards is the same
// in every language.
type Hindi struct {
	Rules map[string]HindiRule
	Cards map[string]string
}

// LoadHindi reads and checks the Hindi files from fsys (rulebook.Files in
// production). Every entry must be written in Devanagari, which catches an
// English line pasted in by mistake. Whether every rule and card has a
// Hindi version is checked by the tests rather than here, so a new English
// rule can ship before its translation (it's shown in English meanwhile).
func LoadHindi(fsys fs.FS) (*Hindi, error) {
	h := &Hindi{}

	if err := readJSONFile(fsys, "etiquette.hi.json", &h.Rules); err != nil {
		return nil, err
	}
	for id, r := range h.Rules {
		if !hasDevanagari(r.Title) || !hasDevanagari(r.Tip) {
			return nil, fmt.Errorf("etiquette.hi.json: rule %q needs a Hindi title and tip", id)
		}
	}

	if err := readJSONFile(fsys, "phrases.hi.json", &h.Cards); err != nil {
		return nil, err
	}
	for id, meaning := range h.Cards {
		if !hasDevanagari(meaning) {
			return nil, fmt.Errorf("phrases.hi.json: card %q needs a Hindi meaning", id)
		}
	}

	return h, nil
}

// Rule returns the rule's title and tip in the given language, falling back
// to English when there's no translation.
func (h *Hindi) Rule(r *EtiquetteRule, language string) (title, tip string) {
	if h != nil && language == "hi" {
		if hr, ok := h.Rules[r.ID]; ok {
			return hr.Title, hr.Tip
		}
	}
	return r.Title, r.Tip
}

// Card returns the card as a Phrase, with its meaning in the given language.
func (h *Hindi) Card(c PhraseCard, language string) Phrase {
	p := c.Phrase()
	if h != nil && language == "hi" {
		if meaning, ok := h.Cards[c.ID]; ok {
			p.English = meaning
		}
	}
	return p
}

func readJSONFile(fsys fs.FS, name string, dst any) error {
	js, err := fs.ReadFile(fsys, name)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(js, dst); err != nil {
		return fmt.Errorf("%s: %w", name, err)
	}
	return nil
}

func hasDevanagari(s string) bool {
	for _, r := range s {
		if unicode.Is(unicode.Devanagari, r) {
			return true
		}
	}
	return false
}
