package data

import (
	"fmt"
	"unicode/utf8"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// MaxMenuLines is the most lines we translate in one request. A whole menu
// page is usually 20 to 50 lines, and the reply has to fit the free tier's
// tokens-per-minute limit.
const MaxMenuLines = 60

// MenuRequest is the text the app read from one menu photo, line by line in
// reading order. Only the text is sent, never the photo.
type MenuRequest struct {
	Lines    []string `json:"lines"`
	Language string   `json:"language"`
}

// MenuLine is one translated line. Japanese is the model's best reading of
// what the menu says, which fixes common text recognition mistakes (響油 for
// 醤油); Text is the line in the traveller's language; Note says what a dish
// is ("chicken and egg simmered on rice"), or is empty.
type MenuLine struct {
	Japanese string `json:"japanese"`
	Text     string `json:"text"`
	Note     string `json:"note"`
}

func ValidateMenuRequest(v *validator.Validator, req *MenuRequest) {
	v.Check(len(req.Lines) >= 1, "lines", "must contain at least 1 line")
	v.Check(len(req.Lines) <= MaxMenuLines, "lines", fmt.Sprintf("must not contain more than %d lines", MaxMenuLines))

	for i, line := range req.Lines {
		key := fmt.Sprintf("lines[%d]", i)
		v.Check(line != "", key, "must be provided")
		v.Check(len(line) <= 200, key, "must not be more than 200 bytes long")
		v.Check(utf8.ValidString(line), key, "must be valid UTF-8")
	}

	if req.Language != "" {
		v.Check(validator.PermittedValue(req.Language, Languages...), "language", mustBeOneOf(Languages))
	}
}

// ValidateMenuLines checks the model's translation before we send it to the
// app: one line out for every line in, each with some text, none too long.
func ValidateMenuLines(v *validator.Validator, lines []MenuLine, want int) {
	v.Check(len(lines) == want, "lines", fmt.Sprintf("must contain exactly %d lines", want))

	for i, l := range lines {
		key := fmt.Sprintf("lines[%d]", i)
		v.Check(l.Text != "", key+".text", "must be provided")
		v.Check(len(l.Text) <= 400, key+".text", "must not be more than 400 bytes long")
		v.Check(len(l.Note) <= 300, key+".note", "must not be more than 300 bytes long")
		v.Check(len(l.Japanese) <= 300, key+".japanese", "must not be more than 300 bytes long")
	}
}
