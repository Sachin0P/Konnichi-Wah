package transit

import (
	"context"
	"os"
	"testing"
	"time"
)

// TestLive calls the real Transitous API. It's skipped unless TRANSIT_LIVE=1,
// so normal test runs stay fast, offline and don't spend the volunteers'
// capacity:
//
//	TRANSIT_LIVE=1 go test ./internal/transit -run Live -v
func TestLive(t *testing.T) {
	if os.Getenv("TRANSIT_LIVE") != "1" {
		t.Skip("set TRANSIT_LIVE=1 to call the real Transitous API")
	}

	c := New(DefaultBaseURL, "KonnichiWah/test (hackathon prototype; github.com/Sachin0P/Konnichi-Wah)", 20*time.Second)
	ctx := context.Background()
	japan := time.FixedZone("JST", 9*60*60)

	trips := []struct {
		from, to         string
		fromNear, toNear LatLng
	}{
		{"Kyoto Station", "Nara Park", LatLng{34.986, 135.759}, LatLng{34.685, 135.805}},
		{"Kyoto Station", "Nara Station", LatLng{34.986, 135.759}, LatLng{34.685, 135.805}},
	}

	for _, trip := range trips {
		from, err := c.Geocode(ctx, trip.from, &trip.fromNear)
		if err != nil {
			t.Errorf("geocode %q: %v", trip.from, err)
			continue
		}
		to, err := c.Geocode(ctx, trip.to, &trip.toNear)
		if err != nil {
			t.Errorf("geocode %q: %v", trip.to, err)
			continue
		}
		t.Logf("%s -> %s (%.4f,%.4f) -> %s (%.4f,%.4f)", trip.from, from.Name, from.Location.Lat, from.Location.Lng, to.Name, to.Location.Lat, to.Location.Lng)

		for _, at := range []time.Time{time.Now(), time.Date(2026, 10, 26, 10, 0, 0, 0, japan)} {
			options, err := c.Route(ctx, from.Location, to.Location, at)
			if err != nil {
				t.Errorf("route at %v: %v", at, err)
				continue
			}
			t.Logf("  leaving %s: %d options", at.In(japan).Format("Mon 15:04"), len(options))
			for _, o := range options {
				t.Logf("    %d min, %d transfers, %s-%s", o.Minutes, o.Transfers, o.Departs, o.Arrives)
				for _, l := range o.Legs {
					t.Logf("      %-8s %-30s %s -> %s", l.Mode, l.Line, l.From, l.To)
				}
			}
		}
	}
}
