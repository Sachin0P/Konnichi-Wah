package gemini

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

func TestRetryAfter(t *testing.T) {
	tests := []struct {
		message string
		want    time.Duration
	}{
		{"Please retry in 6s or upgrade", 6 * time.Second},
		{"Please retry in 4.5s", 4500 * time.Millisecond},
		{"limit: 20 requests per day. Please retry in 13h31m12s or upgrade", 13*time.Hour + 31*time.Minute + 12*time.Second},
		{"Your project has exceeded a quota.", 0},
	}

	for _, tt := range tests {
		if got := (&APIError{StatusCode: 429, Message: tt.message}).RetryAfter(); got != tt.want {
			t.Errorf("RetryAfter(%q) = %v; want %v", tt.message, got, tt.want)
		}
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
			w.WriteHeader(http.StatusTooManyRequests)
			w.Write([]byte(`{"error": {"message": "limit: 20 requests per day. Please retry in 13h31m12s"}}`))
			return
		}
		w.Write([]byte(`{"status": "completed", "steps": [{"type": "model_output", "content": [{"type": "text", "text": "{}"}]}]}`))
	}))
	defer ts.Close()

	c := New(ts.URL, "test-key", []string{"best", "backup"}, 5*time.Second)

	for range 2 {
		if _, err := c.GenerateJSON(context.Background(), "i", "x", map[string]any{}); err != nil {
			t.Fatal(err)
		}
	}

	// The first request tries "best", gets its daily limit and falls back to
	// "backup". The second request skips "best" entirely.
	if want := []string{"best", "backup", "backup"}; !slices.Equal(seen, want) {
		t.Errorf("models tried: %v; want %v", seen, want)
	}
}

func TestPerMinuteLimitDoesNotSwitchModels(t *testing.T) {
	var seen []string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Model string `json:"model"`
		}
		json.NewDecoder(r.Body).Decode(&body)
		seen = append(seen, body.Model)
		w.WriteHeader(http.StatusTooManyRequests)
		w.Write([]byte(`{"error": {"message": "limit: 5 requests per minute. Please retry in 6s"}}`))
	}))
	defer ts.Close()

	_, err := New(ts.URL, "test-key", []string{"best", "backup"}, 5*time.Second).GenerateJSON(context.Background(), "i", "x", map[string]any{})

	// A per-minute limit is left to the caller to retry after a short wait.
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.RetryAfter() != 6*time.Second || !slices.Equal(seen, []string{"best"}) {
		t.Errorf("got %v after trying %v", err, seen)
	}
}

func TestGenerateJSON(t *testing.T) {
	tests := []struct {
		name       string
		status     int
		body       string
		wantText   string
		wantStatus int
	}{
		{
			name:     "Completed",
			status:   http.StatusOK,
			body:     `{"status":"completed","steps":[{"type":"thought","signature":"x"},{"type":"model_output","content":[{"type":"text","text":"{\"a\":"},{"type":"text","text":"1}"}]}]}`,
			wantText: `{"a":1}`,
		},
		{
			name:       "Overloaded",
			status:     http.StatusServiceUnavailable,
			body:       `{"error":{"code":503,"message":"high demand","status":"UNAVAILABLE"}}`,
			wantStatus: http.StatusServiceUnavailable,
		},
		{
			name:       "Rate limited with non-JSON body",
			status:     http.StatusTooManyRequests,
			body:       `slow down`,
			wantStatus: http.StatusTooManyRequests,
		},
		{
			name:   "Not completed",
			status: http.StatusOK,
			body:   `{"status":"failed","steps":[]}`,
		},
		{
			name:   "No output text",
			status: http.StatusOK,
			body:   `{"status":"completed","steps":[{"type":"thought"}]}`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Header.Get("x-goog-api-key") != "test-key" {
					t.Error("api key header not sent")
				}
				w.WriteHeader(tt.status)
				w.Write([]byte(tt.body))
			}))
			defer ts.Close()

			c := New(ts.URL, "test-key", []string{"test-model"}, 5*time.Second)

			text, err := c.GenerateJSON(context.Background(), "instructions", "input", map[string]any{"type": "object"})

			if tt.wantText != "" {
				if err != nil {
					t.Fatal(err)
				}
				if string(text) != tt.wantText {
					t.Errorf("got %q; want %q", text, tt.wantText)
				}
				return
			}

			if err == nil {
				t.Fatal("expected an error, got nil")
			}

			if tt.wantStatus != 0 {
				var apiErr *APIError
				if !errors.As(err, &apiErr) || apiErr.StatusCode != tt.wantStatus {
					t.Errorf("got %v; want APIError with status %d", err, tt.wantStatus)
				}
			}
		})
	}
}
