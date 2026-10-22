package planner

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"slices"
	"testing"
)

// fakeGenerator returns canned output instead of calling the Gemini API.
type fakeGenerator struct {
	output string
	err    error
}

func (f fakeGenerator) GenerateJSON(ctx context.Context, instructions, input string, schema any) ([]byte, error) {
	return []byte(f.output), f.err
}

const validLLMOutput = `{
	"cities": [{"name": "Tokyo", "days": 4, "reason": "r"}, {"name": "Kyoto", "days": 3, "reason": "r"}],
	"suggestions": [
		{"kind": "place", "name": "Senso-ji", "city": "Tokyo", "place_type": "temple", "reason": "r"},
		{"kind": "food", "name": "T's Tantan", "city": "Tokyo", "place_type": "restaurant", "reason": "r"}
	]
}`

func TestGeminiSuggest(t *testing.T) {
	tests := []struct {
		name    string
		output  string
		err     error
		wantErr bool
	}{
		{name: "Valid", output: validLLMOutput},
		{name: "Days don't add up", output: `{"cities": [{"name": "Tokyo", "days": 3, "reason": "r"}], "suggestions": [{"kind": "place", "name": "a", "city": "Tokyo", "place_type": "temple", "reason": "r"}]}`, wantErr: true},
		{name: "Invented place type", output: `{"cities": [{"name": "Tokyo", "days": 7, "reason": "r"}], "suggestions": [{"kind": "place", "name": "a", "city": "Tokyo", "place_type": "castle", "reason": "r"}]}`, wantErr: true},
		{name: "Unknown key", output: `{"cities": [], "suggestions": [], "note": "x"}`, wantErr: true},
		{name: "Not JSON", output: `Sure! Here is your plan...`, wantErr: true},
		{name: "API error", err: errors.New("status 503"), wantErr: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			g := Gemini{Client: fakeGenerator{output: tt.output, err: tt.err}}

			plan, err := g.Suggest(context.Background(), newRequest("veg", nil, []string{"food"}, 7, 4))

			if tt.wantErr {
				if err == nil {
					t.Fatal("expected an error, got nil")
				}
				return
			}

			if err != nil {
				t.Fatal(err)
			}
			if plan.Source != "llm" {
				t.Errorf("got source %q; want %q", plan.Source, "llm")
			}
			if plan.Suggestions[0].ID != "s1" {
				t.Errorf("ids not assigned: got %q", plan.Suggestions[0].ID)
			}

			var ids []string
			for _, s := range plan.Suggestions {
				ids = append(ids, s.ID)
			}

			// The LLM's own food suggestion ("s2", T's Tantan) must be dropped,
			// and food for its cities added from the catalogue instead. The
			// request is veg, so pork ramen must not appear.
			if slices.Contains(ids, "s2") {
				t.Error("llm food suggestion was not dropped")
			}
			for _, id := range []string{"tokyo-ts-tantan", "kyoto-shigetsu"} {
				if !slices.Contains(ids, id) {
					t.Errorf("want catalogue food %q", id)
				}
			}
			if slices.Contains(ids, "tokyo-ichiran") {
				t.Error("veg traveller was given pork ramen")
			}
		})
	}
}

func TestFallback(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	req := newRequest("veg", nil, []string{"food"}, 7, 4)

	tests := []struct {
		name       string
		primary    Planner
		wantSource string
	}{
		{name: "Primary works", primary: Gemini{Client: fakeGenerator{output: validLLMOutput}}, wantSource: "llm"},
		{name: "Primary bad output", primary: Gemini{Client: fakeGenerator{output: `{}`}}, wantSource: "catalogue"},
		{name: "Primary API error", primary: Gemini{Client: fakeGenerator{err: errors.New("status 429")}}, wantSource: "catalogue"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			f := Fallback{Primary: tt.primary, Backup: Catalogue{}, Logger: logger}

			plan, err := f.Suggest(context.Background(), req)
			if err != nil {
				t.Fatal(err)
			}
			if plan.Source != tt.wantSource {
				t.Errorf("got source %q; want %q", plan.Source, tt.wantSource)
			}
		})
	}
}
