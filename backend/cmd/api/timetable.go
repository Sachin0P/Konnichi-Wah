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

// The getTimetableHandler returns live upcoming departures for a train station
// in Japan, either by station name or by latitude/longitude.
//
//	GET /v1/transit/timetable?station=Tokyo+Station&city=Tokyo&n=10
//	GET /v1/transit/timetable?lat=35.6812&lng=139.7671&n=10
func (app *application) getTimetableHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Station string
		Lat     float64
		Lng     float64
		City    string
		N       int
	}

	v := validator.New()
	qs := r.URL.Query()

	input.Station = strings.TrimSpace(app.readString(qs, "station", ""))
	input.Lat = app.readFloat(qs, "lat", 0, v)
	input.Lng = app.readFloat(qs, "lng", 0, v)
	input.City = strings.TrimSpace(app.readString(qs, "city", ""))
	input.N = app.readInt(qs, "n", 10, v)

	hasCoords := qs.Has("lat") && qs.Has("lng")

	v.Check(hasCoords || input.Station != "", "station", "must be provided, or lat and lng")
	v.Check(len(input.Station) <= 100, "station", "must not be more than 100 bytes long")
	v.Check(input.N >= 1 && input.N <= 30, "n", "must be between 1 and 30")

	if hasCoords {
		v.Check(inJapan(input.Lat, input.Lng), "lat", "must be a point in Japan")
	}

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	// If coordinates weren't passed directly, resolve station name
	lat, lng := input.Lat, input.Lng
	stationName := input.Station

	if !hasCoords {
		cityCentre := transit.CityCentre(input.City)
		if cityCentre == nil {
			cityCentre = &transit.LatLng{Lat: 35.681, Lng: 139.767}
		}

		// Try geocoding station
		found, err := app.transit.Geocode(ctx, input.Station, cityCentre)
		if err == nil {
			lat = found.Location.Lat
			lng = found.Location.Lng
			if stationName == "" {
				stationName = found.Name
			}
		} else {
			// Fallback 1: Local station snapshot
			if stationMatches := data.SuggestStations(app.stations, input.Station, cityCentre.Lat, cityCentre.Lng, 1); len(stationMatches) > 0 {
				lat = stationMatches[0].Lat
				lng = stationMatches[0].Lng
				stationName = stationMatches[0].Name
			} else if app.photon != nil {
				// Fallback 2: Photon geocoding
				if photonMatches, pErr := app.photon.Suggest(ctx, input.Station, cityCentre.Lat, cityCentre.Lng, 1); pErr == nil && len(photonMatches) > 0 {
					lat = photonMatches[0].Lat
					lng = photonMatches[0].Lng
					stationName = photonMatches[0].Name
				}
			}
		}
	}

	if lat == 0 && lng == 0 {
		v.AddError("station", "could not find this station; try typing a station name like Tokyo or Osaka")
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	tt, err := app.transit.Timetable(ctx, stationName, lat, lng, input.City, input.N)
	if err != nil {
		switch {
		case errors.Is(err, transit.ErrNotFound):
			v.AddError("station", "could not find this station; try typing a station name like Tokyo or Osaka")
			app.failedValidationResponse(w, r, v.Errors)
		default:
			app.logError(r, err)
			app.serviceUnavailableResponse(w, r, "the timetable service is not responding right now, please try again in a minute")
		}
		return
	}

	env := envelope{
		"timetable": tt,
	}

	err = app.writeJSON(w, http.StatusOK, env, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
