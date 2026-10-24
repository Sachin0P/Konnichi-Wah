package websearch

import (
	"context"
	"testing"
	"time"
)

func TestSearchDuckDuckGo(t *testing.T) {
	client := NewClient(10 * time.Second)
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	results, err := client.Search(ctx, "top things to do in Shibuya Tokyo", 3)
	if err != nil {
		t.Logf("Search failed (may be offline in test environment): %v", err)
		return
	}

	if len(results) == 0 {
		t.Log("No search results returned (network could be restricted)")
		return
	}

	for i, r := range results {
		t.Logf("Result %d: Title=%q URL=%q Snippet=%q", i+1, r.Title, r.URL, r.Snippet)
		if r.Title == "" {
			t.Errorf("Result %d had empty title", i)
		}
		if r.Snippet == "" {
			t.Errorf("Result %d had empty snippet", i)
		}
	}
}
