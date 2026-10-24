package main

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// The listDirectionsHandler finds up to three ways to get from one place to
// another by train, subway, bus and on foot, with the walk to the station,
// the exit to use and the path of every leg for the app's map. Either end can
// be a place name (looked up near the city) or a point, so the app can route
// "from my location".
//
//	GET /v1/directions?from=Shinjuku+Gyoen&to=Senso-ji&city=Tokyo&depart=09:00
//	GET /v1/directions?from_lat=35.68&from_lng=139.71&to=Senso-ji&city=Tokyo
func (app *application) listDirectionsHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		From, To         string
		FromLat, FromLng float64
		ToLat, ToLng     float64
		City             string
		Depart           string
	}

	v := validator.New()
	qs := r.URL.Query()

	input.From = strings.TrimSpace(app.readString(qs, "from", ""))
	input.To = strings.TrimSpace(app.readString(qs, "to", ""))
	input.FromLat = app.readFloat(qs, "from_lat", 0, v)
	input.FromLng = app.readFloat(qs, "from_lng", 0, v)
	input.ToLat = app.readFloat(qs, "to_lat", 0, v)
	input.ToLng = app.readFloat(qs, "to_lng", 0, v)
	input.City = strings.TrimSpace(app.readString(qs, "city", ""))
	input.Depart = app.readString(qs, "depart", "")

	fromPoint := qs.Has("from_lat") && qs.Has("from_lng")
	toPoint := qs.Has("to_lat") && qs.Has("to_lng")

	v.Check(fromPoint || input.From != "", "from", "must be provided, or from_lat and from_lng")
	v.Check(toPoint || input.To != "", "to", "must be provided, or to_lat and to_lng")
	v.Check(len(input.From) <= 100, "from", "must not be more than 100 bytes long")
	v.Check(len(input.To) <= 100, "to", "must not be more than 100 bytes long")
	if fromPoint {
		v.Check(inJapan(input.FromLat, input.FromLng), "from_lat", "must be a point in Japan")
	}
	if toPoint {
		v.Check(inJapan(input.ToLat, input.ToLng), "to_lat", "must be a point in Japan")
	}
	if input.Depart != "" {
		_, err := time.Parse("15:04", input.Depart)
		v.Check(err == nil, "depart", "must be a time like 09:00")
	}

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	defer cancel()

	// Each end: a point as it is, or a name looked up near the city.
	ends := []struct {
		key, name string
		point     bool
		lat, lng  float64
		place     transit.LatLng
		label     string
	}{
		{key: "from", name: input.From, point: fromPoint, lat: input.FromLat, lng: input.FromLng},
		{key: "to", name: input.To, point: toPoint, lat: input.ToLat, lng: input.ToLng},
	}
	for i := range ends {
		e := &ends[i]
		if e.point {
			e.place = transit.LatLng{Lat: e.lat, Lng: e.lng}
			// A name sent with a point labels it ("Hotel"); otherwise
			// it's where the traveller is.
			e.label = e.name
			if e.label == "" {
				e.label = "Your location"
			}
			continue
		}

		found, err := app.transit.Geocode(ctx, e.name, transit.CityCentre(input.City))
		if err != nil {
			var fallbackLat, fallbackLng float64
			var fallbackFound bool

			cityCentre := transit.CityCentre(input.City)
			if cityCentre == nil {
				cityCentre = &transit.LatLng{Lat: 35.681, Lng: 139.767}
			}

			// Fallback 1: Local station snapshot
			if stationMatches := data.SuggestStations(app.stations, e.name, cityCentre.Lat, cityCentre.Lng, 1); len(stationMatches) > 0 {
				fallbackLat = stationMatches[0].Lat
				fallbackLng = stationMatches[0].Lng
				fallbackFound = true
			}

			// Fallback 2: Photon geocoding
			if !fallbackFound && app.photon != nil {
				if photonMatches, pErr := app.photon.Suggest(ctx, e.name, cityCentre.Lat, cityCentre.Lng, 1); pErr == nil && len(photonMatches) > 0 {
					fallbackLat = photonMatches[0].Lat
					fallbackLng = photonMatches[0].Lng
					fallbackFound = true
				}
			}

			if fallbackFound {
				e.place = transit.LatLng{Lat: fallbackLat, Lng: fallbackLng}
				e.label = e.name
				continue
			}

			switch {
			case errors.Is(err, transit.ErrNotFound):
				v.AddError(e.key, "could not find this place; try a nearby station or landmark")
				app.failedValidationResponse(w, r, v.Errors)
			default:
				app.logError(r, err)
				app.serviceUnavailableResponse(w, r, "the route planner is not responding right now, please try again in a minute")
			}
			return
		}
		e.place = found.Location
		e.label = e.name
	}

	journeys, err := app.transit.Directions(ctx, ends[0].place, ends[1].place, ends[0].label, ends[1].label, transit.DepartAt(time.Now(), input.Depart))
	if err != nil {
		app.logError(r, err)
		app.serviceUnavailableResponse(w, r, "the route planner is not responding right now, please try again in a minute")
		return
	}

	env := envelope{"directions": map[string]any{
		"from":     map[string]any{"name": ends[0].label, "lat": ends[0].place.Lat, "lng": ends[0].place.Lng},
		"to":       map[string]any{"name": ends[1].label, "lat": ends[1].place.Lat, "lng": ends[1].place.Lng},
		"journeys": journeys,
		// Transitous asks for credit to itself and the transit operators;
		// the walking routes come from OpenStreetMap.
		"attribution": "Routes: Transitous and the transit operators. Map data © OpenStreetMap contributors",
	}}

	err = app.writeJSON(w, http.StatusOK, env, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

// inJapan is a rough box around Japan, to catch swapped or missing
// coordinates before they reach the route planner.
func inJapan(lat, lng float64) bool {
	return lat >= 24 && lat <= 46 && lng >= 122 && lng <= 146
}
