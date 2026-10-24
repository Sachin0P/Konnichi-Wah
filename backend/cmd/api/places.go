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

// The listPlacesHandler finds places near a point or a named place: either a
// kind of help (hospital, pharmacy, police...) from our OpenStreetMap
// snapshot, or anything else by free text through Photon.
//
//	GET /v1/places?kind=hospital&lat=35.71&lng=139.79
//	GET /v1/places?q=laundromat&near=Senso-ji&city=Tokyo
func (app *application) listPlacesHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Kind, Query string
		Lat, Lng    float64
		Near, City  string
	}

	v := validator.New()
	qs := r.URL.Query()

	input.Kind = app.readString(qs, "kind", "")
	input.Query = strings.TrimSpace(app.readString(qs, "q", ""))
	input.Lat = app.readFloat(qs, "lat", 0, v)
	input.Lng = app.readFloat(qs, "lng", 0, v)
	input.Near = strings.TrimSpace(app.readString(qs, "near", ""))
	input.City = strings.TrimSpace(app.readString(qs, "city", ""))

	hasPoint := qs.Has("lat") && qs.Has("lng")

	v.Check((input.Kind == "") != (input.Query == ""), "kind", "provide either kind or q")
	if input.Kind != "" {
		v.Check(validator.PermittedValue(input.Kind, data.HelpKinds...), "kind", "must be one of: "+strings.Join(data.HelpKinds, ", "))
	}
	v.Check(len(input.Query) <= 100, "q", "must not be more than 100 bytes long")
	v.Check(hasPoint || input.Near != "", "near", "must be provided, or lat and lng")
	if hasPoint {
		v.Check(inJapan(input.Lat, input.Lng), "lat", "must be a point in Japan")
	}
	v.Check(len(input.Near) <= 100, "near", "must not be more than 100 bytes long")

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	centre := transit.LatLng{Lat: input.Lat, Lng: input.Lng}
	nearName := "your location"
	if !hasPoint {
		found, err := app.transit.Geocode(ctx, input.Near, transit.CityCentre(input.City))
		if err != nil {
			switch {
			case errors.Is(err, transit.ErrNotFound):
				v.AddError("near", "could not find this place; try a nearby station or landmark")
				app.failedValidationResponse(w, r, v.Errors)
			default:
				app.logError(r, err)
				app.serviceUnavailableResponse(w, r, "place search is not responding right now, please try again in a minute")
			}
			return
		}
		centre, nearName = found.Location, input.Near
	}

	results := []data.PlaceSummary{}
	source := "snapshot"

	if input.Kind != "" {
		places, err := data.Nearest(app.models.Places, centre.Lat, centre.Lng, input.Kind, 3, 10)
		if err != nil {
			app.serverErrorResponse(w, r, err)
			return
		}
		for _, p := range places {
			results = append(results, data.Summarize(p, input.Kind))
		}
	} else {
		source = "search"
		found, err := app.photon.Search(ctx, input.Query, centre.Lat, centre.Lng, 10)
		if err != nil {
			// Photon is a shared service and often unreachable; say so
			// rather than pretend nothing exists.
			app.logError(r, err)
			app.serviceUnavailableResponse(w, r, "place search is not responding right now, please try again in a minute")
			return
		}
		for _, p := range found {
			results = append(results, data.PlaceSummary{
				Name: p.Name, Kind: p.Kind, Lat: p.Lat, Lng: p.Lng, Address: p.Address,
				DistanceM: data.DistanceM(centre.Lat, centre.Lng, p.Lat, p.Lng),
			})
		}
	}

	env := envelope{"places": map[string]any{
		"near":    map[string]any{"name": nearName, "lat": centre.Lat, "lng": centre.Lng},
		"results": results,
		"source":  source,
		// OpenStreetMap's licence requires this credit wherever its data is
		// shown.
		"attribution": "Map data © OpenStreetMap contributors",
	}}

	err := app.writeJSON(w, http.StatusOK, env, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
