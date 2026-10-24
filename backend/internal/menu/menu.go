// Package menu translates the text of a Japanese menu with an LLM.
//
// The app reads the menu on the phone (ML Kit text recognition) and sends us
// only the text, never the photo. Translating the whole menu in one request
// gives the model context that the app's on-device translator, which sees
// one line at a time, doesn't have: it knows 親子丼 is oyakodon rather than
// "parent and child", and can tell when text recognition misread a word.
//
// The model only translates and explains. The app's diet warnings still come
// from its own word list, matched against the Japanese.
package menu

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"unicode"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/groq"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// ErrBadOutput means the model's reply wasn't the JSON we asked for (wrong
// shape, or a different number of lines). The app falls back to its
// on-device translation.
var ErrBadOutput = errors.New("menu: the model's reply was not a valid translation")

// LLM is the part of the Groq client the translator uses, so tests can use a
// fake.
type LLM interface {
	JSON(ctx context.Context, messages []groq.Message, maxTokens int) (string, error)
}

type Translator struct {
	LLM LLM
}

// maxChunkSize is the maximum number of lines sent to the LLM in one prompt.
// Keeping it small (15 lines) prevents token exhaustion, response truncation,
// and line-skipping by local and cloud models.
const maxChunkSize = 15

// Translate returns one translated line for each line of the menu, in order.
func (t Translator) Translate(ctx context.Context, req data.MenuRequest) ([]data.MenuLine, error) {
	if len(req.Lines) == 0 {
		return []data.MenuLine{}, nil
	}

	if len(req.Lines) <= maxChunkSize {
		return t.translateChunk(ctx, req.Lines, req.Language)
	}

	allLines := make([]data.MenuLine, 0, len(req.Lines))
	for i := 0; i < len(req.Lines); i += maxChunkSize {
		end := i + maxChunkSize
		if end > len(req.Lines) {
			end = len(req.Lines)
		}
		chunk := req.Lines[i:end]

		chunkLines, err := t.translateChunk(ctx, chunk, req.Language)
		if err != nil {
			return nil, err
		}
		allLines = append(allLines, chunkLines...)
	}

	return allLines, nil
}

func (t Translator) translateChunk(ctx context.Context, lines []string, language string) ([]data.MenuLine, error) {
	var input strings.Builder
	for i, line := range lines {
		fmt.Fprintf(&input, "%d. %s\n", i+1, line)
	}

	instructions := baseInstructions
	if language == "hi" {
		instructions += hindiInstructions
	}

	// 500 + 80 tokens per line is generous for translation and a short note.
	maxTokens := 500 + 80*len(lines)
	reply, err := t.LLM.JSON(ctx, []groq.Message{
		{Role: "system", Content: instructions},
		{Role: "user", Content: input.String()},
	}, maxTokens)
	if err != nil {
		return nil, err
	}

	var out struct {
		Lines []data.MenuLine `json:"lines"`
	}
	if err := json.Unmarshal([]byte(reply), &out); err != nil {
		return nil, fmt.Errorf("%w: %v", ErrBadOutput, err)
	}

	for i := range out.Lines {
		out.Lines[i].Japanese = strings.TrimSpace(out.Lines[i].Japanese)
		out.Lines[i].Text = strings.TrimSpace(out.Lines[i].Text)
		out.Lines[i].Note = strings.TrimSpace(out.Lines[i].Note)

		// If the model left text empty for a price, symbol, or isolated kana fragment (e.g. の, ちの), keep the original line
		if out.Lines[i].Text == "" && i < len(lines) && isPriceOrFragment(lines[i]) {
			out.Lines[i].Text = strings.TrimSpace(lines[i])
		}
	}

	v := validator.New()
	if data.ValidateMenuLines(v, out.Lines, len(lines)); !v.Valid() {
		return nil, fmt.Errorf("%w: %v", ErrBadOutput, v.Errors)
	}

	return out.Lines, nil
}

func isPriceOrFragment(s string) bool {
	s = strings.TrimSpace(s)
	if s == "" {
		return true
	}
	if isPriceOrSymbol(s) {
		return true
	}
	// Isolated Japanese particles or short reading fragments (e.g. の, ちの) that models leave untranslated
	runes := []rune(s)
	if len(runes) <= 3 && isKanaOnly(runes) {
		return true
	}
	return false
}

func isKanaOnly(runes []rune) bool {
	for _, r := range runes {
		if !unicode.In(r, unicode.Hiragana, unicode.Katakana) && r != 'ー' {
			return false
		}
	}
	return len(runes) > 0
}

func isPriceOrSymbol(s string) bool {
	s = strings.TrimSpace(s)
	if s == "" {
		return true
	}
	hasDigit := false
	for _, r := range s {
		if unicode.IsDigit(r) {
			hasDigit = true
		} else if r == '¥' || r == '￥' || r == '円' || r == '$' || r == '€' || r == ',' || r == '.' || r == '-' || r == ' ' {
			continue
		} else {
			return false
		}
	}
	return hasDigit
}

const baseInstructions = `You translate Japanese restaurant menus for Indian travellers in Japan.

You get the numbered lines that text recognition read from one photo of a menu, in reading order. Text recognition makes mistakes, especially with vertical or handwritten menus: characters can be misread (響油 for 醤油, 井 for 丼, 天原ら for 天ぷら) or split. Use the rest of the menu as context to work out what each line most likely says.

For every input line, give:
- japanese: your best reading of the line in Japanese, with recognition mistakes fixed. If you can't tell, copy the line.
- text: the line in natural English. Use the usual English name for well-known dishes (Oyakodon, Tempura, Edamame, Tonkatsu) rather than a word-by-word translation. Keep prices as they are (¥850). If the line is just a price, a number or unreadable, repeat it in text. 'text' MUST NOT be empty.
- note: for a dish, what it is in under 12 words, e.g. "chicken and egg simmered with onion, on rice". For anything else (headings, prices, notices), an empty string.

Don't add warnings or advice about ingredients or diets; the app does that.

Reply with only this JSON, with exactly one entry per input line, in the same order:
{"lines": [{"japanese": "...", "text": "...", "note": "..."}]}`

const hindiInstructions = `

The traveller reads Hindi: write text and note in simple, everyday Hindi in Devanagari script. Keep dish names in their usual English letters (Oyakodon, Tempura), and prices as they are.`
