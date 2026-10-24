package groq

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"
	"time"
)

func TestChatSendsToolsAndParsesToolCalls(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-key" {
			t.Error("api key not sent")
		}

		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if body["tool_choice"] != "none" {
			t.Errorf("tool_choice = %v; want none", body["tool_choice"])
		}
		if body["reasoning_effort"] != "low" {
			t.Errorf("reasoning_effort not set for a gpt-oss model")
		}
		tools := body["tools"].([]any)
		if fn := tools[0].(map[string]any)["function"].(map[string]any); fn["name"] != "get_etiquette" {
			t.Errorf("got tool %v", fn)
		}

		w.Write([]byte(`{"choices": [{"message": {"role": "assistant", "content": "",
			"tool_calls": [{"id": "call_1", "type": "function", "function": {"name": "get_etiquette", "arguments": "{\"place_type\": \"temple\"}"}}]}}]}`))
	}))
	defer ts.Close()

	c := New(ts.URL, "test-key", []string{"openai/gpt-oss-120b"}, 5*time.Second)
	tools := []Tool{{Name: "get_etiquette", Parameters: map[string]any{"type": "object"}}}

	msg, err := c.Chat(context.Background(), []Message{{Role: "user", Content: "Temple tips?"}}, tools, false)
	if err != nil {
		t.Fatal(err)
	}
	if len(msg.ToolCalls) != 1 || msg.ToolCalls[0].Function.Arguments != `{"place_type": "temple"}` {
		t.Errorf("got %+v", msg.ToolCalls)
	}
}

func TestDailyLimitMovesToTheNextModel(t *testing.T) {
	var seen []string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Model string `json:"model"`
		}
		json.NewDecoder(r.Body).Decode(&body)
		seen = append(seen, body.Model)

		if body.Model == "best" {
			w.Header().Set("retry-after", "3600")
			w.WriteHeader(http.StatusTooManyRequests)
			w.Write([]byte(`{"error": {"message": "Rate limit reached for requests per day"}}`))
			return
		}
		w.Write([]byte(`{"choices": [{"message": {"role": "assistant", "content": "hi"}}]}`))
	}))
	defer ts.Close()

	c := New(ts.URL, "test-key", []string{"best", "backup"}, 5*time.Second)
	for range 2 {
		if _, err := c.Chat(context.Background(), []Message{{Role: "user", Content: "hi"}}, nil, true); err != nil {
			t.Fatal(err)
		}
	}

	if want := []string{"best", "backup", "backup"}; !slices.Equal(seen, want) {
		t.Errorf("models tried: %v; want %v", seen, want)
	}
}

func TestPerMinuteLimitIsReturnedWithRetryAfter(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("retry-after", "7")
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error": {"message": "Rate limit reached for tokens per minute"}}`))
	}))
	defer ts.Close()

	_, err := New(ts.URL, "test-key", []string{"best", "backup"}, 5*time.Second).Chat(context.Background(), []Message{{Role: "user", Content: "hi"}}, nil, true)

	var apiErr *APIError
	if !errors.As(err, &apiErr) || !apiErr.Temporary() || apiErr.RetryAfter() != 7*time.Second {
		t.Errorf("got %v", err)
	}
}

func TestJSONAsksForJSONMode(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)

		if rf, _ := body["response_format"].(map[string]any); rf["type"] != "json_object" {
			t.Errorf("response_format = %v; want json_object", body["response_format"])
		}
		if body["max_completion_tokens"] != float64(500) {
			t.Errorf("max_completion_tokens = %v", body["max_completion_tokens"])
		}

		w.Write([]byte(`{"choices": [{"message": {"role": "assistant", "content": "{\"ok\": true}"}}]}`))
	}))
	defer ts.Close()

	got, err := New(ts.URL, "k", []string{"openai/gpt-oss-120b"}, 5*time.Second).JSON(context.Background(), []Message{{Role: "user", Content: "hi"}}, 500)
	if err != nil || got != `{"ok": true}` {
		t.Errorf("got %q, %v", got, err)
	}
}

func TestReasoningOverride(t *testing.T) {
	var got any
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		got = body["reasoning_effort"]
		w.Write([]byte(`{"choices": [{"message": {"role": "assistant", "content": "{}"}}]}`))
	}))
	defer ts.Close()

	c := New(ts.URL, "", []string{"qwen3.5:9b"}, 5*time.Second)
	c.Reasoning = "none"
	if _, err := c.JSON(context.Background(), []Message{{Role: "user", Content: "hi"}}, 100); err != nil || got != "none" {
		t.Errorf("reasoning_effort = %v, err %v; want none", got, err)
	}
}

func TestGenerateJSONSendsTheSchema(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		rf := body["response_format"].(map[string]any)
		if rf["type"] != "json_schema" || rf["json_schema"].(map[string]any)["schema"] == nil {
			t.Errorf("response_format = %v", rf)
		}
		w.Write([]byte(`{"choices": [{"message": {"role": "assistant", "content": "{\"cities\": []}"}}]}`))
	}))
	defer ts.Close()

	out, err := New(ts.URL, "", []string{"m"}, 5*time.Second).GenerateJSON(context.Background(), "plan", "a trip", map[string]any{"type": "object"})
	if err != nil || string(out) != `{"cities": []}` {
		t.Errorf("got %s, %v", out, err)
	}
}
