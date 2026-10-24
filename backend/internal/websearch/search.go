package websearch

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"
)

// Result is one web search result with its title, snippet, and link.
type Result struct {
	Title   string `json:"title"`
	Snippet string `json:"snippet"`
	URL     string `json:"url"`
}

// Searcher performs web searches to find locations, recommendations, and information.
type Searcher interface {
	Search(ctx context.Context, query string, maxResults int) ([]Result, error)
}

// Client implements Searcher using available search providers (Tavily, Serper, or DuckDuckGo).
type Client struct {
	httpClient *http.Client
}

// NewClient returns a new Client with a custom timeout.
func NewClient(timeout time.Duration) *Client {
	if timeout <= 0 {
		timeout = 8 * time.Second
	}
	return &Client{
		httpClient: &http.Client{Timeout: timeout},
	}
}

var (
	tagRe     = regexp.MustCompile(`<[^>]+>`)
	titleRe   = regexp.MustCompile(`(?s)<a[^>]*class="result__a"[^>]*>(.*?)</a>`)
	snippetRe = regexp.MustCompile(`(?s)<a[^>]*class="result__snippet"[^>]*>(.*?)</a>`)
	uddgRe    = regexp.MustCompile(`uddg=([^&"]+)`)
)

func cleanHTML(s string) string {
	s = tagRe.ReplaceAllString(s, "")
	s = html.UnescapeString(s)
	return strings.Join(strings.Fields(s), " ")
}

// Search queries the web for the given query and returns up to maxResults results.
func (c *Client) Search(ctx context.Context, query string, maxResults int) ([]Result, error) {
	if maxResults <= 0 {
		maxResults = 5
	}

	// 1. Check for Tavily API key
	if tavilyKey := os.Getenv("TAVILY_API_KEY"); tavilyKey != "" {
		results, err := c.searchTavily(ctx, tavilyKey, query, maxResults)
		if err == nil && len(results) > 0 {
			return results, nil
		}
	}

	// 2. Check for Serper API key (Google Search)
	if serperKey := os.Getenv("SERPER_API_KEY"); serperKey != "" {
		results, err := c.searchSerper(ctx, serperKey, query, maxResults)
		if err == nil && len(results) > 0 {
			return results, nil
		}
	}

	// 3. Fallback to DuckDuckGo HTML search (no API key required)
	return c.searchDuckDuckGo(ctx, query, maxResults)
}

func (c *Client) searchDuckDuckGo(ctx context.Context, query string, maxResults int) ([]Result, error) {
	searchURL := "https://html.duckduckgo.com/html/?q=" + url.QueryEscape(query)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return nil, err
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("duckduckgo: unexpected status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20)) // 1 MB limit
	if err != nil {
		return nil, err
	}

	content := string(body)

	// Split by result wrapper
	parts := strings.Split(content, "class=\"result results_links")
	if len(parts) <= 1 {
		return nil, nil
	}

	var results []Result
	for i := 1; i < len(parts) && len(results) < maxResults; i++ {
		part := parts[i]

		var title string
		if m := titleRe.FindStringSubmatch(part); len(m) > 1 {
			title = cleanHTML(m[1])
		}

		var snippet string
		if m := snippetRe.FindStringSubmatch(part); len(m) > 1 {
			snippet = cleanHTML(m[1])
		}

		var link string
		if m := uddgRe.FindStringSubmatch(part); len(m) > 1 {
			if decoded, err := url.QueryUnescape(m[1]); err == nil {
				link = decoded
			}
		}

		// Skip ads and sponsored links
		if strings.Contains(link, "duckduckgo.com/y.js") || strings.Contains(link, "bing.com/aclick") || strings.Contains(part, "badge--ad") {
			continue
		}

		if title != "" && snippet != "" {
			results = append(results, Result{
				Title:   title,
				Snippet: snippet,
				URL:     link,
			})
		}
	}

	return results, nil
}

func (c *Client) searchTavily(ctx context.Context, apiKey, query string, maxResults int) ([]Result, error) {
	reqBody := map[string]any{
		"api_key":             apiKey,
		"query":               query,
		"max_results":         maxResults,
		"search_depth":        "basic",
		"include_answer":      false,
		"include_raw_content": false,
	}

	js, err := json.Marshal(reqBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.tavily.com/search", bytes.NewReader(js))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("tavily: status %d", resp.StatusCode)
	}

	var data struct {
		Results []struct {
			Title   string `json:"title"`
			URL     string `json:"url"`
			Content string `json:"content"`
		} `json:"results"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}

	results := make([]Result, 0, len(data.Results))
	for _, r := range data.Results {
		results = append(results, Result{
			Title:   r.Title,
			Snippet: r.Content,
			URL:     r.URL,
		})
	}
	return results, nil
}

func (c *Client) searchSerper(ctx context.Context, apiKey, query string, maxResults int) ([]Result, error) {
	reqBody := map[string]any{
		"q":   query,
		"num": maxResults,
	}

	js, err := json.Marshal(reqBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://google.serper.dev/search", bytes.NewReader(js))
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-API-KEY", apiKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("serper: status %d", resp.StatusCode)
	}

	var data struct {
		Organic []struct {
			Title   string `json:"title"`
			Snippet string `json:"snippet"`
			Link    string `json:"link"`
		} `json:"organic"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}

	results := make([]Result, 0, len(data.Organic))
	for _, r := range data.Organic {
		results = append(results, Result{
			Title:   r.Title,
			Snippet: r.Snippet,
			URL:     r.Link,
		})
	}
	return results, nil
}
