package transit

import "strings"

// StationNames gives English names to the stations that the timetable data
// names only in Japanese (the Kansai operators and the Toei subway). It's
// built from the OpenStreetMap station snapshot, osmdata/stations.json, where
// most stations have a name:en.
type StationNames struct {
	byName map[string][]station
}

type station struct {
	english  string
	lat, lng float64
}

// Station is one entry from the snapshot.
type Station struct {
	Name, NameEn string
	Lat, Lng     float64
}

func NewStationNames(stations []Station) *StationNames {
	s := &StationNames{byName: make(map[string][]station)}
	for _, st := range stations {
		if st.NameEn == "" {
			continue
		}
		key := stationKey(st.Name)
		s.byName[key] = append(s.byName[key], station{english: st.NameEn, lat: st.Lat, lng: st.Lng})
	}
	return s
}

// English returns the English name of the station called ja nearest to the
// point, or "" if there's none within 2 km. Many station names repeat across
// Japan (there's more than one 日本橋), so the distance check matters.
func (s *StationNames) English(ja string, lat, lng float64) string {
	return s.EnglishWithin(ja, lat, lng, 2)
}

// EnglishWithin is English with a wider search, for a train's destination
// ("towards 本八幡"), which can be far from where you get on.
func (s *StationNames) EnglishWithin(ja string, lat, lng, km float64) string {
	if s == nil {
		return ""
	}
	best, bestKm := "", km
	for _, st := range s.byName[stationKey(ja)] {
		if d := distanceKm(LatLng{lat, lng}, LatLng{st.lat, st.lng}); d < bestKm {
			best, bestKm = st.english, d
		}
	}
	return best
}

// stationKey matches names with or without a trailing 駅 ("京都駅" and "京都").
func stationKey(name string) string {
	return strings.TrimSuffix(strings.TrimSpace(name), "駅")
}
