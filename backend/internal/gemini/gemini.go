package gemini

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"maps"
	"net/http"
	"regexp"
	"strings"
	"sync"
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

// Temporary reports whether retrying later might work: a rate limit or the
// model being overloaded, both common on the free tier.
func (e *APIError) Temporary() bool {
	return e.StatusCode == http.StatusTooManyRequests || e.StatusCode == http.StatusServiceUnavailable
}

// retryHintRX matches the wait Google suggests in a rate limit message, e.g.
// "Please retry in 6s", "retry in 4.2s" or, for a daily limit, "retry in
// 13h31m12s". The captured part is in Go's time.ParseDuration format.
var retryHintRX = regexp.MustCompile(`retry in ((?:[0-9]+h)?(?:[0-9]+m)?(?:[0-9]+(?:\.[0-9]+)?s))`)

// RetryAfter returns how long Google asked us to wait before retrying, or 0
// if the error didn't say. A per-minute limit asks for a few seconds; a
// daily limit asks for hours.
func (e *APIError) RetryAfter() time.Duration {
	m := retryHintRX.FindStringSubmatch(e.Message)
	if m == nil {
		return 0
	}
	d, err := time.ParseDuration(m[1])
	if err != nil {
		return 0
	}
	return d
}

// dailyLimitThreshold separates a per-minute limit (wait a moment and retry)
// from a daily one (give up on this model until tomorrow).
const dailyLimitThreshold = 2 * time.Minute

func (e *APIError) dailyLimit() bool {
	return e.StatusCode == http.StatusTooManyRequests && e.RetryAfter() > dailyLimitThreshold
}

// Client is a minimal Gemini client built on net/http. We call the REST API
// directly instead of using Google's SDK, which keeps our dependencies small
// and makes it easy to switch provider later.
//
// It takes a list of models, best first. Google's free tier gives each model
// its own small daily quota (20 requests a day for gemini-3.6-flash in
// testing), so when one model's daily quota runs out, the client remembers
// that until it resets and moves on to the next model. The planner and the
// companion share one Client, so they share this knowledge.
type Client struct {
	baseURL string
	apiKey  string
	models  []string
	http    *http.Client

	mu        sync.Mutex
	exhausted map[string]time.Time // model -> when its daily quota resets
}

// New returns a Client. The timeout is applied to every request, so a slow or
// stuck model can never hold up one of our handlers indefinitely.
func New(baseURL, apiKey string, models []string, timeout time.Duration) *Client {
	return &Client{
		baseURL:   baseURL,
		apiKey:    apiKey,
		models:    models,
		http:      &http.Client{Timeout: timeout},
		exhausted: make(map[string]time.Time),
	}
}

// available returns the models whose daily quota hasn't run out, in order.
func (c *Client) available() []string {
	c.mu.Lock()
	defer c.mu.Unlock()

	var models []string
	for _, m := range c.models {
		if time.Now().After(c.exhausted[m]) {
			models = append(models, m)
		}
	}
	return models
}

func (c *Client) markExhausted(model string, until time.Time) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.exhausted[model] = until
}

// Tool declares a function the model may call. Parameters is a JSON schema.
type Tool struct {
	Name        string
	Description string
	Parameters  map[string]any
}

// FunctionCall is the model asking us to run one of our tools.
type FunctionCall struct {
	ID        string          `json:"id"`
	Name      string          `json:"name"`
	Arguments json.RawMessage `json:"arguments"`
}

// Turn is one response from the model. Steps are kept as raw JSON because, in
// a stateless conversation, they must be sent back to the API exactly as
// received (they carry the model's "thought signatures").
type Turn struct {
	Status string
	Steps  []json.RawMessage
	Text   string
	Calls  []FunctionCall
}

// UserInput, ModelOutput and FunctionResult build the input steps for Chat.
// The API takes the whole conversation as a list of steps on every request.
func UserInput(text string) json.RawMessage {
	return mustMarshal(map[string]any{"type": "user_input", "content": text})
}

func ModelOutput(text string) json.RawMessage {
	return mustMarshal(map[string]any{
		"type":    "model_output",
		"content": []map[string]any{{"type": "text", "text": text}},
	})
}

// FunctionResult reports a tool's result back to the model. The result is
// sent as JSON text.
func FunctionResult(call FunctionCall, result any) json.RawMessage {
	js, err := json.Marshal(result)
	if err != nil {
		js, _ = json.Marshal(map[string]string{"error": err.Error()})
	}
	return mustMarshal(map[string]any{
		"type":    "function_result",
		"name":    call.Name,
		"call_id": call.ID,
		"result":  []map[string]any{{"type": "text", "text": string(js)}},
	})
}

// Chat sends a conversation (as steps) with the tools the model may call. If
// the model wants to call tools, the returned Turn has Calls set and Status
// "requires_action": run them, then call Chat again with the input plus
// turn.Steps plus a FunctionResult for each call.
func (c *Client) Chat(ctx context.Context, instructions string, input []json.RawMessage, tools []Tool) (*Turn, error) {
	declarations := make([]map[string]any, len(tools))
	for i, t := range tools {
		declarations[i] = map[string]any{
			"type":        "function",
			"name":        t.Name,
			"description": t.Description,
			"parameters":  t.Parameters,
		}
	}

	reqBody := map[string]any{
		"system_instruction": instructions,
		"input":              input,
	}
	if len(declarations) > 0 {
		reqBody["tools"] = declarations
	}

	return c.interact(ctx, reqBody)
}

// GenerateJSON sends the instructions and input to the model and returns its
// reply, which the API constrains to match the given JSON schema. The caller
// must still validate the result: the schema controls the shape, but not
// whether the content makes sense.
func (c *Client) GenerateJSON(ctx context.Context, instructions, input string, schema any) ([]byte, error) {
	turn, err := c.interact(ctx, map[string]any{
		"system_instruction": instructions,
		"input":              input,
		"response_format": map[string]any{
			"type":      "text",
			"mime_type": "application/json",
			"schema":    schema,
		},
	})
	if err != nil {
		return nil, err
	}

	if turn.Status != "completed" {
		return nil, fmt.Errorf("gemini: interaction status %q", turn.Status)
	}
	if turn.Text == "" {
		return nil, errors.New("gemini: response contained no output text")
	}

	return []byte(turn.Text), nil
}

// ErrNoModels is returned when every model's daily quota has run out.
var ErrNoModels = &APIError{StatusCode: http.StatusTooManyRequests, Message: "every configured model has used its daily quota"}

// interact sends the request to the first model that still has quota, moving
// on to the next one if a model reports that its daily limit is used up.
func (c *Client) interact(ctx context.Context, reqBody map[string]any) (*Turn, error) {
	models := c.available()
	if len(models) == 0 {
		return nil, ErrNoModels
	}

	var err error
	for _, model := range models {
		var turn *Turn
		turn, err = c.interactWith(ctx, model, reqBody)

		var apiErr *APIError
		if errors.As(err, &apiErr) && apiErr.dailyLimit() {
			c.markExhausted(model, time.Now().Add(apiErr.RetryAfter()))
			continue
		}
		return turn, err
	}

	return nil, err
}

// interactWith sends one request to one model and decodes the reply.
func (c *Client) interactWith(ctx context.Context, model string, reqBody map[string]any) (*Turn, error) {
	reqBody = maps.Clone(reqBody)
	reqBody["model"] = model
	// A low thinking level keeps response times reasonable: our tasks are
	// mostly recall and following rules, not long multi-step reasoning.
	reqBody["generation_config"] = map[string]any{"thinking_level": "low"}
	// We send the whole conversation every time, so ask Google not to store
	// the interaction.
	reqBody["store"] = false

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

	// An interaction is a list of steps (thoughts, function calls, output).
	// We decode each step twice: once as raw JSON to send back later, and
	// once into a struct to read the parts we need.
	var interaction struct {
		Status string            `json:"status"`
		Steps  []json.RawMessage `json:"steps"`
	}

	err = json.Unmarshal(body, &interaction)
	if err != nil {
		return nil, fmt.Errorf("gemini: decoding response: %w", err)
	}

	turn := &Turn{Status: interaction.Status, Steps: interaction.Steps}

	var text strings.Builder
	for _, raw := range interaction.Steps {
		var step struct {
			Type    string `json:"type"`
			Content []struct {
				Type string `json:"type"`
				Text string `json:"text"`
			} `json:"content"`
			FunctionCall
		}
		if json.Unmarshal(raw, &step) != nil {
			continue
		}

		switch step.Type {
		case "model_output":
			for _, part := range step.Content {
				if part.Type == "text" {
					text.WriteString(part.Text)
				}
			}
		case "function_call":
			turn.Calls = append(turn.Calls, step.FunctionCall)
		}
	}
	turn.Text = text.String()

	return turn, nil
}

func mustMarshal(v any) json.RawMessage {
	js, err := json.Marshal(v)
	if err != nil {
		// Only possible for values that can't be represented in JSON, which
		// would be a bug in this package.
		panic(err)
	}
	return js
}
