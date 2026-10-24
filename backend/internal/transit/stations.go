package transit

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
	clean := CleanStationName(ja)
	if clean == "" {
		return ""
	}
	best, bestKm := "", km
	for _, st := range s.byName[clean] {
		if lat == 0 && lng == 0 {
			return st.english
		}
		if d := distanceKm(LatLng{lat, lng}, LatLng{st.lat, st.lng}); d < bestKm {
			best, bestKm = st.english, d
		}
	}
	if best != "" {
		return best
	}

	// If no match within the tight distance, but entries exist for this exact cleaned name:
	// If all entries for this name share the same English name (e.g. all "神田" are "Kanda"),
	// return that English name.
	if entries := s.byName[clean]; len(entries) > 0 {
		firstEn := entries[0].english
		allSame := true
		for _, e := range entries[1:] {
			if e.english != firstEn {
				allSame = false
				break
			}
		}
		if allSame {
			return firstEn
		}
		fallbackKm := 50.0
		for _, st := range entries {
			if d := distanceKm(LatLng{lat, lng}, LatLng{st.lat, st.lng}); d < fallbackKm {
				best, fallbackKm = st.english, d
			}
		}
		if best != "" {
			return best
		}
		return firstEn
	}

	return ""
}

// stationKey matches names with or without a trailing 駅, company prefixes, or parenthesized notes.
func stationKey(name string) string {
	return CleanStationName(name)
}
