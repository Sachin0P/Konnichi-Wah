package groq

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

// DefaultBaseURL is Groq's OpenAI-compatible chat completions endpoint.
const DefaultBaseURL = "https://api.groq.com/openai/v1/chat/completions"

// APIError is returned when Groq responds with a non-200 status.
type APIError struct {
	StatusCode int
	Message    string
	retryAfter time.Duration
}

func (e *APIError) Error() string {
	return fmt.Sprintf("groq: status %d: %s", e.StatusCode, e.Message)
}

// Temporary reports whether retrying later might work: a rate limit or the
// service being overloaded.
func (e *APIError) Temporary() bool {
	return e.StatusCode == http.StatusTooManyRequests || e.StatusCode == http.StatusServiceUnavailable
}

// RetryAfter is how long Groq asked us to wait (its retry-after header), or
// 0 if it didn't say.
func (e *APIError) RetryAfter() time.Duration {
	return e.retryAfter
}

// dailyLimitThreshold separates a per-minute limit (wait a moment) from a
// daily one (give up on this model until it resets).
const dailyLimitThreshold = 2 * time.Minute

// ErrNoModels is returned when every configured model has hit its daily limit.
var ErrNoModels = &APIError{StatusCode: http.StatusTooManyRequests, Message: "every configured model has used its daily quota"}

// Message is one chat message in the OpenAI format Groq uses.
type Message struct {
	Role       string     `json:"role"`
	Content    string     `json:"content"`
	ToolCalls  []ToolCall `json:"tool_calls,omitempty"`
	ToolCallID string     `json:"tool_call_id,omitempty"`
	Name       string     `json:"name,omitempty"`
}

// ToolCall is the model asking us to run a tool. Arguments is a JSON string.
type ToolCall struct {
	ID       string `json:"id"`
	Type     string `json:"type"`
	Function struct {
		Name      string `json:"name"`
		Arguments string `json:"arguments"`
	} `json:"function"`
}

// Tool declares a function the model may call. Parameters is a JSON schema.
type Tool struct {
	Name        string
	Description string
	Parameters  map[string]any
}

// Client is a minimal Groq client built on net/http, like our Gemini client.
// It takes a list of models, best first, and moves on to the next when one
// reports that its daily limit is used up.
type Client struct {
	baseURL string
	apiKey  string
	models  []string
	http    *http.Client

	mu        sync.Mutex
	exhausted map[string]time.Time
}

func New(baseURL, apiKey string, models []string, timeout time.Duration) *Client {
	return &Client{
		baseURL:   baseURL,
		apiKey:    apiKey,
		models:    models,
		http:      &http.Client{Timeout: timeout},
		exhausted: make(map[string]time.Time),
	}
}

// Chat sends the conversation and returns the model's reply, which either
// has Content (the answer) or ToolCalls. With allowTools false the tools are
// still declared (the conversation may refer to earlier calls) but the model
// is told not to call any, so it has to answer.
func (c *Client) Chat(ctx context.Context, messages []Message, tools []Tool, allowTools bool) (*Message, error) {
	reqBody := map[string]any{
		"messages": messages,
		// Replies are short by design; this also keeps us inside the free
		// tier's tokens-per-minute limit.
		"max_completion_tokens": 1024,
		"temperature":           0.3,
	}

	if len(tools) > 0 {
		decls := make([]map[string]any, len(tools))
		for i, t := range tools {
			decls[i] = map[string]any{
				"type": "function",
				"function": map[string]any{
					"name":        t.Name,
					"description": t.Description,
					"parameters":  t.Parameters,
				},
			}
		}
		reqBody["tools"] = decls
		reqBody["tool_choice"] = "auto"
		if !allowTools {
			reqBody["tool_choice"] = "none"
		}
	}

	models := c.available()
	if len(models) == 0 {
		return nil, ErrNoModels
	}

	var err error
	for _, model := range models {
		var msg *Message
		msg, err = c.chatWith(ctx, model, reqBody)

		var apiErr *APIError
		if errors.As(err, &apiErr) && apiErr.StatusCode == http.StatusTooManyRequests && apiErr.RetryAfter() > dailyLimitThreshold {
			c.markExhausted(model, time.Now().Add(apiErr.RetryAfter()))
			continue
		}
		return msg, err
	}

	return nil, err
}

func (c *Client) chatWith(ctx context.Context, model string, reqBody map[string]any) (*Message, error) {
	body := make(map[string]any, len(reqBody)+2)
	for k, v := range reqBody {
		body[k] = v
	}
	body["model"] = model

	// The gpt-oss models think before answering. "low" keeps replies fast,
	// which matters more for a chat on a phone than deep reasoning.
	if strings.HasPrefix(model, "openai/gpt-oss") {
		body["reasoning_effort"] = "low"
	}

	js, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL, bytes.NewReader(js))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.apiKey)

	res, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	resBody, err := io.ReadAll(io.LimitReader(res.Body, 1_048_576))
	if err != nil {
		return nil, err
	}

	if res.StatusCode != http.StatusOK {
		var errBody struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}

		message := http.StatusText(res.StatusCode)
		if json.Unmarshal(resBody, &errBody) == nil && errBody.Error.Message != "" {
			message = errBody.Error.Message
		}

		// retry-after is in seconds, and only sent with a 429.
		var retryAfter time.Duration
		if s, err := strconv.ParseFloat(res.Header.Get("retry-after"), 64); err == nil {
			retryAfter = time.Duration(s * float64(time.Second))
		}

		return nil, &APIError{StatusCode: res.StatusCode, Message: message, retryAfter: retryAfter}
	}

	var completion struct {
		Choices []struct {
			Message Message `json:"message"`
		} `json:"choices"`
	}

	err = json.Unmarshal(resBody, &completion)
	if err != nil {
		return nil, fmt.Errorf("groq: decoding response: %w", err)
	}
	if len(completion.Choices) == 0 {
		return nil, errors.New("groq: response contained no choices")
	}

	return &completion.Choices[0].Message, nil
}

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
