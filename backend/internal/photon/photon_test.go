package photon

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestSearch(t *testing.T) {
	calls := 0
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		q := r.URL.Query()
		if q.Get("q") != "launderette" || q.Get("bbox") == "" || !strings.HasPrefix(r.Header.Get("User-Agent"), "KonnichiWah") {
			t.Errorf("request %s, UA %q", r.URL.RawQuery, r.Header.Get("User-Agent"))
		}
		w.Write([]byte(`{"features": [
			{"geometry": {"coordinates": [139.79, 35.71]}, "properties": {"name": "Coin Laundry", "osm_value": "laundry", "street": "Kaminarimon", "city": "Taito"}},
			{"geometry": {"coordinates": [139.80, 35.72]}, "properties": {"osm_value": "laundry"}}
		]}`))
	}))
	defer ts.Close()

	c := New(ts.URL+"/api/", "KonnichiWah/test", 5*time.Second)
	places, err := c.Search(context.Background(), "launderette", 35.71, 139.79, 5)
	if err != nil {
		t.Fatal(err)
	}

	// The nameless result is dropped; coordinates come back as lat, lng.
	if len(places) != 1 || places[0].Name != "Coin Laundry" || places[0].Lat != 35.71 || places[0].Address != "Kaminarimon, Taito" {
		t.Errorf("got %+v", places)
	}

	// The same search again comes from the cache.
	if _, err := c.Search(context.Background(), "Launderette", 35.71, 139.79, 5); err != nil || calls != 1 {
		t.Errorf("calls = %d, err %v; want the cached result", calls, err)
	}
}

func TestSearchUsesReverseSearchForCategories(t *testing.T) {
	var path, tag string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path, tag = r.URL.Path, r.URL.Query().Get("osm_tag")
		w.Write([]byte(`{"features": []}`))
	}))
	defer ts.Close()

	c := New(ts.URL+"/api/", "KonnichiWah/test", 5*time.Second)
	c.Search(context.Background(), "Supermarket", 35.71, 139.79, 5)
	if path != "/reverse" || tag != "shop:supermarket" {
		t.Errorf("searched %s with osm_tag %q; want /reverse with shop:supermarket", path, tag)
	}
	c.Search(context.Background(), "Uniqlo", 35.71, 139.79, 5)
	if path != "/api/" || tag != "" {
		t.Errorf("searched %s with osm_tag %q for a name; want /api/ with none", path, tag)
	}
}
