package menu

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/groq"
)

// fakeLLM returns a fixed reply and records what it was asked.
type fakeLLM struct {
	reply    string
	messages []groq.Message
}

func (f *fakeLLM) JSON(ctx context.Context, messages []groq.Message, maxTokens int) (string, error) {
	f.messages = messages
	return f.reply, nil
}

func TestTranslate(t *testing.T) {
	llm := &fakeLLM{reply: `{"lines": [
		{"japanese": "親子丼 ¥950", "text": "Oyakodon ¥950", "note": "chicken and egg on rice"},
		{"japanese": "醤油ラーメン", "text": " Soy sauce ramen ", "note": ""}
	]}`}

	lines, err := Translator{LLM: llm}.Translate(context.Background(), data.MenuRequest{Lines: []string{"親子井 ¥950", "響油ラーメン"}})
	if err != nil {
		t.Fatal(err)
	}

	if lines[0].Text != "Oyakodon ¥950" || lines[1].Japanese != "醤油ラーメン" || lines[1].Text != "Soy sauce ramen" {
		t.Errorf("got %+v", lines)
	}
	// The lines go to the model numbered, in order.
	if !strings.Contains(llm.messages[1].Content, "1. 親子井 ¥950\n2. 響油ラーメン") {
		t.Errorf("sent %q", llm.messages[1].Content)
	}
	if strings.Contains(llm.messages[0].Content, "Hindi") {
		t.Error("English request asked for Hindi")
	}
}

func TestTranslateAsksForHindi(t *testing.T) {
	llm := &fakeLLM{reply: `{"lines": [{"japanese": "枝豆", "text": "Edamame (उबली सोयाबीन)", "note": ""}]}`}

	if _, err := (Translator{LLM: llm}).Translate(context.Background(), data.MenuRequest{Lines: []string{"枝豆"}, Language: "hi"}); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(llm.messages[0].Content, "Devanagari") {
		t.Error("Hindi request didn't ask for Hindi")
	}
}

func TestTranslatePriceFallback(t *testing.T) {
	// If the model left text empty for a price line, it should fall back to the price string
	llm := &fakeLLM{reply: `{"lines": [{"japanese": "450 円", "text": "", "note": ""}]}`}

	lines, err := (Translator{LLM: llm}).Translate(context.Background(), data.MenuRequest{Lines: []string{"450 円"}})
	if err != nil {
		t.Fatalf("expected success with price fallback, got: %v", err)
	}
	if len(lines) != 1 || lines[0].Text != "450 円" {
		t.Errorf("expected text '450 円', got %+v", lines)
	}
}

// A reply the app can't line up with its photo must be refused, so the app
// falls back to its own translation rather than labelling the wrong lines.
func TestTranslateRejectsBadOutput(t *testing.T) {
	tests := map[string]string{
		"not JSON":       `Here is your translation`,
		"too few lines":  `{"lines": [{"japanese": "a", "text": "A", "note": ""}]}`,
		"empty text":     `{"lines": [{"japanese": "a", "text": "", "note": ""}, {"japanese": "b", "text": "B", "note": ""}]}`,
		"no lines field": `{"translations": []}`,
	}

	for name, reply := range tests {
		t.Run(name, func(t *testing.T) {
			_, err := Translator{LLM: &fakeLLM{reply: reply}}.Translate(context.Background(), data.MenuRequest{Lines: []string{"a", "b"}})
			if !errors.Is(err, ErrBadOutput) {
				t.Errorf("err = %v; want ErrBadOutput", err)
			}
		})
	}
}
