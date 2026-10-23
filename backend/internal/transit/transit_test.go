package transit

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestStripStation(t *testing.T) {
	tests := []struct {
		in          string
		want        string
		wantStation bool
	}{
		{"Shinjuku Station", "Shinjuku", true},
		{"kyoto station", "kyoto", true},
		{"新宿駅", "新宿", true},
		{"Fushimi Inari Taisha", "Fushimi Inari Taisha", false},
	}

	for _, tt := range tests {
		got, station := stripStation(tt.in)
		if got != tt.want || station != tt.wantStation {
			t.Errorf("stripStation(%q) = %q, %v; want %q, %v", tt.in, got, station, tt.want, tt.wantStation)
		}
	}
}

func TestGeocodeSkipsWrongCountryAndFarAwayMatches(t *testing.T) {
	calls := 0
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Header.Get("User-Agent") != "test-agent" {
			t.Error("user agent not sent")
		}
		if r.URL.Query().Get("type") != "STOP" || r.URL.Query().Get("text") != "Shinjuku" {
			t.Errorf("unexpected query: %s", r.URL.RawQuery)
		}
		w.Write([]byte(`[
			{"name": "Shinjuku Station, Brazil", "type": "STOP", "country": "BR", "lat": -23.5, "lon": -46.6},
			{"name": "Shinjuku, Osaka", "type": "STOP", "country": "JP", "lat": 34.70, "lon": 135.50},
			{"name": "Shinjuku", "type": "STOP", "country": "JP", "lat": 35.690, "lon": 139.700}
		]`))
	}))
	defer ts.Close()

	c := New(ts.URL, "test-agent", 5*time.Second)
	tokyo := &LatLng{35.681, 139.767}

	place, err := c.Geocode(context.Background(), "Shinjuku Station", tokyo)
	if err != nil {
		t.Fatal(err)
	}
	if place.Name != "Shinjuku" {
		t.Errorf("got %q; want the Tokyo match", place.Name)
	}

	// The second lookup must come from the cache.
	_, _ = c.Geocode(context.Background(), "Shinjuku Station", tokyo)
	if calls != 1 {
		t.Errorf("got %d calls to Transitous; want 1", calls)
	}
}

func TestGeocodeNotFound(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`[{"name": "Paris", "type": "PLACE", "country": "FR", "lat": 48.8, "lon": 2.3}]`))
	}))
	defer ts.Close()

	_, err := New(ts.URL, "test-agent", 5*time.Second).Geocode(context.Background(), "Atlantis", nil)
	if !errors.Is(err, ErrNotFound) {
		t.Errorf("got %v; want ErrNotFound", err)
	}
}

func TestRouteSimplifiesAndDeduplicates(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Two itineraries on the same lines (should become one option) and
		// one on a different line.
		leg := func(line, dep, arr string) string {
			return `{"mode": "WALK", "duration": 300, "from": {"name": "Start", "departure": "` + dep + `"}, "to": {"name": "Asakusa", "arrival": "` + dep + `"}},
			        {"mode": "SUBWAY", "duration": 1200, "routeShortName": "G", "routeLongName": "` + line + `", "agencyName": "Tokyo Metro", "headsign": "Shibuya",
			         "from": {"name": "Asakusa", "departure": "` + dep + `"}, "to": {"name": "Shibuya", "arrival": "` + arr + `"}}`
		}
		w.Write([]byte(`{"itineraries": [
			{"duration": 1800, "transfers": 0, "startTime": "2026-10-26T01:00:00Z", "endTime": "2026-10-26T01:30:00Z", "legs": [` + leg("Ginza Line", "2026-10-26T01:00:00Z", "2026-10-26T01:30:00Z") + `]},
			{"duration": 1800, "transfers": 0, "startTime": "2026-10-26T01:05:00Z", "endTime": "2026-10-26T01:35:00Z", "legs": [` + leg("Ginza Line", "2026-10-26T01:05:00Z", "2026-10-26T01:35:00Z") + `]},
			{"duration": 2400, "transfers": 1, "startTime": "2026-10-26T01:00:00Z", "endTime": "2026-10-26T01:40:00Z", "legs": [` + leg("Asakusa Line", "2026-10-26T01:00:00Z", "2026-10-26T01:40:00Z") + `]}
		]}`))
	}))
	defer ts.Close()

	options, err := New(ts.URL, "test-agent", 5*time.Second).Route(context.Background(), LatLng{35.71, 139.79}, LatLng{35.65, 139.70}, time.Now())
	if err != nil {
		t.Fatal(err)
	}

	if len(options) != 2 {
		t.Fatalf("got %d options; want 2 after removing the duplicate", len(options))
	}

	first := options[0]
	if first.Minutes != 30 || first.Departs != "10:00" || first.Arrives != "10:30" {
		t.Errorf("got %+v; want 30 minutes, 10:00 to 10:30 Japan time", first)
	}

	train := first.Legs[1]
	if train.Line != "G Ginza Line" || train.Operator != "Tokyo Metro" || train.Towards != "Shibuya" || train.Mode != "subway" {
		t.Errorf("got %+v", train)
	}
}
