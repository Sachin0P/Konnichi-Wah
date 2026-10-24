package transit

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestTimetableParsing(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "stoptimes") {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{
				"place": {
					"name": "東京 Tōkyō",
					"lat": 35.6813,
					"lon": 139.7670
				},
				"stopTimes": [
					{
						"place": {
							"name": "東京 Tōkyō",
							"stopCode": "JC01",
							"departure": "2026-10-24T19:38:00Z",
							"cancelled": false
						},
						"mode": "REGIONAL_RAIL",
						"realTime": true,
						"headsign": "(快速 Rapid) 高尾 Takao",
						"routeShortName": "JC",
						"routeLongName": "JR中央線快速 JR Chūō Rapid Line",
						"routeColor": "ff5c1e",
						"agencyName": "JR東日本 JR East"
					}
				]
			}`))
			return
		}
		http.NotFound(w, r)
	}))
	defer server.Close()

	client := New(server.URL+"/v1", "TestApp/1.0", 5*time.Second)

	tt, err := client.Timetable(context.Background(), "Tokyo", 35.6813, 139.7670, "Tokyo", 5)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if tt.StationEn != "Tōkyō" && tt.Station != "Tōkyō" && tt.Station != "Tokyo" {
		t.Errorf("got station %q, want Tōkyō", tt.Station)
	}

	if len(tt.Departures) != 1 {
		t.Fatalf("got %d departures, want 1", len(tt.Departures))
	}

	d := tt.Departures[0]
	if d.Time != "04:38" {
		t.Errorf("got time %q, want 04:38", d.Time)
	}
	if d.Platform != "JC01" {
		t.Errorf("got platform %q, want JC01", d.Platform)
	}
	if d.LineColor != "#ff5c1e" {
		t.Errorf("got line color %q, want #ff5c1e", d.LineColor)
	}
	if d.Towards != "Takao" {
		t.Errorf("got towards %q, want Takao", d.Towards)
	}
	if !d.RealTime {
		t.Errorf("expected real_time true, got false")
	}
}
