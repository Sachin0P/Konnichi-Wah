package gemini

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// DefaultBaseURL is the endpoint for Google's Interactions API, which Google now
// recommends over the older generateContent endpoint for new projects.
const DefaultBaseURL = "https://generativelanguage.googleapis.com/v1beta/interactions"

// APIError is returned when the Gemini API responds with a non-200 status. We
// keep the status code so callers can tell a rate limit (429) or overload
// (503) apart from a bug in our request (400).
type APIError struct {
	StatusCode int
	Message    string
}

func (e *APIError) Error() string {
	return fmt.Sprintf("gemini: status %d: %s", e.StatusCode, e.Message)
}

// Client is a minimal Gemini client built on net/http. We call the REST API
// directly instead of using Google's SDK, which keeps our dependencies small
// and makes it easy to switch provider later.
type Client struct {
	baseURL string
	apiKey  string
	model   string
	http    *http.Client
}

// New returns a Client. The timeout is applied to every request, so a slow or
// stuck model can never hold up one of our handlers indefinitely.
func New(baseURL, apiKey, model string, timeout time.Duration) *Client {
	return &Client{
		baseURL: baseURL,
		apiKey:  apiKey,
		model:   model,
		http:    &http.Client{Timeout: timeout},
	}
}

// GenerateJSON sends the instructions and input to the model and returns its
// reply, which the API constrains to match the given JSON schema. The caller
// must still validate the result: the schema controls the shape, but not
// whether the content makes sense.
func (c *Client) GenerateJSON(ctx context.Context, instructions, input string, schema any) ([]byte, error) {
	reqBody := map[string]any{
		"model":              c.model,
		"system_instruction": instructions,
		"input":              input,
		"response_format": map[string]any{
			"type":      "text",
			"mime_type": "application/json",
			"schema":    schema,
		},
		// A low thinking level keeps response times reasonable. The planner's
		// task is mostly recall, not multi-step reasoning.
		"generation_config": map[string]any{
			"thinking_level": "low",
		},
		// We don't use follow-up turns, so ask Google not to store the
		// interaction.
		"store": false,
	}

	js, err := json.Marshal(reqBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL, bytes.NewReader(js))
	if err != nil {
		return nil, err
	}

	// Send the key in a header rather than the URL, so it doesn't end up in
	// any logs that record URLs.
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-goog-api-key", c.apiKey)

	res, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	// Limit how much we read, in case something goes badly wrong upstream.
	body, err := io.ReadAll(io.LimitReader(res.Body, 1_048_576))
	if err != nil {
		return nil, err
	}

	if res.StatusCode != http.StatusOK {
		var errBody struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}

		// If the error body isn't the JSON we expect, fall back to the raw
		// status text rather than failing to report the error at all.
		message := http.StatusText(res.StatusCode)
		if json.Unmarshal(body, &errBody) == nil && errBody.Error.Message != "" {
			message = errBody.Error.Message
		}

		return nil, &APIError{StatusCode: res.StatusCode, Message: message}
	}

	// An interaction is a list of steps (thoughts, tool calls and so on). The
	// answer is in the text parts of the "model_output" step.
	var interaction struct {
		Status string `json:"status"`
		Steps  []struct {
			Type    string `json:"type"`
			Content []struct {
				Type string `json:"type"`
				Text string `json:"text"`
			} `json:"content"`
		} `json:"steps"`
	}

	err = json.Unmarshal(body, &interaction)
	if err != nil {
		return nil, fmt.Errorf("gemini: decoding response: %w", err)
	}

	if interaction.Status != "completed" {
		return nil, fmt.Errorf("gemini: interaction status %q", interaction.Status)
	}

	var text strings.Builder
	for _, step := range interaction.Steps {
		if step.Type != "model_output" {
			continue
		}
		for _, part := range step.Content {
			if part.Type == "text" {
				text.WriteString(part.Text)
			}
		}
	}

	if text.Len() == 0 {
		return nil, errors.New("gemini: response contained no output text")
	}

	return []byte(text.String()), nil
}
