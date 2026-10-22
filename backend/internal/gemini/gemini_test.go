package gemini

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

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

			c := New(ts.URL, "test-key", "test-model", 5*time.Second)

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
