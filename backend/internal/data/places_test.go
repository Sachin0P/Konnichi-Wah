package data

import "testing"

// fakeFinder has a single hospital, 2.5 km away.
type fakeFinder struct{ radii []int }

func (f *fakeFinder) Near(lat, lng float64, radiusM int, kind string) ([]*NearbyPlace, error) {
	f.radii = append(f.radii, radiusM)
	if radiusM < 2500 {
		return []*NearbyPlace{}, nil
	}
	return []*NearbyPlace{{Place: &Place{Name: "聖路加国際病院", NameEn: "St. Luke's International Hospital", Tags: map[string]string{"amenity": "hospital", "emergency": "yes"}}, DistanceM: 2500}}, nil
}

func TestNearestWidensTheSearch(t *testing.T) {
	f := &fakeFinder{}
	places, err := Nearest(f, 35.67, 139.77, "hospital", 1, 5)
	if err != nil {
		t.Fatal(err)
	}
	if len(places) != 1 || len(f.radii) != 3 {
		t.Errorf("got %d places after searching %v", len(places), f.radii)
	}

	s := Summarize(places[0], "hospital")
	if s.Name != "St. Luke's International Hospital" || s.NameJa != "聖路加国際病院" || !s.Emergency {
		t.Errorf("summary = %+v", s)
	}
}

func TestIsFood(t *testing.T) {
	if (&Place{Tags: map[string]string{"amenity": "hospital"}}).IsFood() {
		t.Error("a hospital isn't somewhere to eat")
	}
	if !(&Place{Tags: map[string]string{"amenity": "cafe"}}).IsFood() {
		t.Error("a cafe is somewhere to eat")
	}
}
