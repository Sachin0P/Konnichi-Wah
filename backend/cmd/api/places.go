package main

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

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
			name := p.Name
			var nameJa string
			if data.HasJapaneseRune(name) {
				nameJa = name
				name = data.EnglishPlaceName(name, p.Kind)
			}
			results = append(results, data.PlaceSummary{
				Name: name, NameJa: nameJa, Kind: p.Kind, Lat: p.Lat, Lng: p.Lng, Address: p.Address,
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

// The listPlaceSuggestionsHandler completes a half-typed place name for the
// search-as-you-type dropdown on the directions screen. Stations come from
// our snapshot (instant, and they're what most people type); landmarks and
// everything else come from Photon. It's called on every pause in typing, so
// it never fails because of Photon: without it, the stations still come back.
//
//	GET /v1/places/suggest?q=sensoj&city=Tokyo
func (app *application) listPlaceSuggestionsHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Query, City string
		Lat, Lng    float64
	}

	v := validator.New()
	qs := r.URL.Query()

	input.Query = strings.TrimSpace(app.readString(qs, "q", ""))
	input.City = strings.TrimSpace(app.readString(qs, "city", ""))
	input.Lat = app.readFloat(qs, "lat", 0, v)
	input.Lng = app.readFloat(qs, "lng", 0, v)

	v.Check(utf8.RuneCountInString(input.Query) >= 2, "q", "must be at least 2 characters long")
	v.Check(len(input.Query) <= 100, "q", "must not be more than 100 bytes long")

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	// Rank places near the traveller first, else near the city, else near
	// central Tokyo.
	centre := transit.LatLng{Lat: input.Lat, Lng: input.Lng}
	if !qs.Has("lat") || !inJapan(input.Lat, input.Lng) {
		centre = transit.LatLng{Lat: 35.681, Lng: 139.767}
		if c := transit.CityCentre(input.City); c != nil {
			centre = *c
		}
	}

	const limit = 8
	suggestions := data.SuggestStations(app.stations, input.Query, centre.Lat, centre.Lng, 4)

	// Photon matches the start of words, so it's only worth asking once
	// there's a little more to go on.
	if utf8.RuneCountInString(input.Query) >= 3 {
		ctx, cancel := context.WithTimeout(r.Context(), 4*time.Second)
		defer cancel()

		found, err := app.photon.Suggest(ctx, input.Query, centre.Lat, centre.Lng, limit)
		if err != nil {
			app.logger.Warn("place suggestions without photon", "error", err.Error())
		}
		for _, p := range found {
			if len(suggestions) == limit {
				break
			}
			// Photon knows the stations too; we've listed those already.
			if p.Kind == "station" || p.Kind == "halt" || p.Kind == "stop" {
				continue
			}
			// Photon's matching is fuzzy. For input in Latin letters, keep
			// only names that really start the way the traveller is typing.
			if isASCII(input.Query) && !data.NameMatches(p.Name, input.Query) {
				continue
			}
			suggestions = append(suggestions, data.PlaceSuggestion{
				Name: p.Name, Kind: p.Kind, Detail: p.Address, Lat: p.Lat, Lng: p.Lng,
			})
		}
	}

	err := app.writeJSON(w, http.StatusOK, envelope{"suggestions": suggestions}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

func isASCII(s string) bool {
	for _, r := range s {
		if r > unicode.MaxASCII {
			return false
		}
	}
	return true
}
