package main

import (
	"errors"
	"net/http"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/desimatch"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// The listFoodHandler finds restaurants near a point or a named place and
// ranks them with Desi Match for the traveller's diet:
//
//	GET /v1/food?near=Senso-ji&city=Tokyo&diet=veg&avoid=onion_garlic
//	GET /v1/food?lat=35.7148&lng=139.7967&diet=halal
//
// The brief has the profile coming from the auth token. There's no auth yet,
// so for now the app sends diet, avoid and spice as query parameters.
func (app *application) listFoodHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Lat     float64
		Lng     float64
		Near    string
		City    string
		RadiusM int
		Profile data.UserProfile
	}

	v := validator.New()
	qs := r.URL.Query()

	input.Lat = app.readFloat(qs, "lat", 0, v)
	input.Lng = app.readFloat(qs, "lng", 0, v)
	input.Near = strings.TrimSpace(app.readString(qs, "near", ""))
	input.City = strings.TrimSpace(app.readString(qs, "city", ""))
	input.RadiusM = int(app.readFloat(qs, "radius", 1000, v))
	input.Profile.Diet = app.readString(qs, "diet", "non_veg")
	input.Profile.Avoid = app.readCSV(qs, "avoid", []string{})
	input.Profile.SpiceLevel = app.readString(qs, "spice", "medium")

	hasPoint := input.Lat != 0 || input.Lng != 0
	v.Check(hasPoint || input.Near != "", "near", "must be provided, or lat and lng")
	if hasPoint {
		v.Check(input.Lat >= 24 && input.Lat <= 46 && input.Lng >= 122 && input.Lng <= 146, "lat", "must be a point in Japan")
	}
	v.Check(len(input.Near) <= 100, "near", "must not be more than 100 bytes long")
	v.Check(input.RadiusM >= 100 && input.RadiusM <= 3000, "radius", "must be between 100 and 3000")
	v.Check(validator.PermittedValue(input.Profile.Diet, data.Diets...), "diet", "must be one of: "+strings.Join(data.Diets, ", "))
	for _, item := range input.Profile.Avoid {
		v.Check(validator.PermittedValue(item, data.AvoidItems...), "avoid", "must be one of: "+strings.Join(data.AvoidItems, ", "))
	}
	v.Check(validator.PermittedValue(input.Profile.SpiceLevel, data.SpiceLevels...), "spice", "must be one of: "+strings.Join(data.SpiceLevels, ", "))

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	// Turn a place name into a point, aiming at the city if we know it.
	centre := transit.LatLng{Lat: input.Lat, Lng: input.Lng}
	nearName := ""
	if !hasPoint {
		place, err := app.transit.Geocode(r.Context(), input.Near, transit.CityCentre(input.City))
		if err != nil {
			switch {
			case errors.Is(err, transit.ErrNotFound):
				v.AddError("near", "could not find that place")
				app.failedValidationResponse(w, r, v.Errors)
			default:
				app.logError(r, err)
				app.serviceUnavailableResponse(w, r, "place search is not available right now, please try again")
			}
			return
		}
		centre, nearName = place.Location, place.Name
	}

	// With an explicit radius, search exactly that far. Otherwise widen the
	// search (1, 2, then 3 km) until there are a few good results.
	var results []desimatch.Result
	if qs.Has("radius") {
		places, err := app.models.Places.Near(centre.Lat, centre.Lng, input.RadiusM, true)
		if err != nil {
			app.serverErrorResponse(w, r, err)
			return
		}
		results = desimatch.Rank(places, input.Profile, 10)
	} else {
		var err error
		results, err = desimatch.Search(app.models.Places, centre.Lat, centre.Lng, input.Profile, 10)
		if err != nil {
			app.serverErrorResponse(w, r, err)
			return
		}
	}

	env := envelope{"food": map[string]any{
		"near":    map[string]any{"name": nearName, "lat": centre.Lat, "lng": centre.Lng},
		"results": results,
		// OpenStreetMap's licence requires this credit wherever its data is
		// shown.
		"attribution": "Map data © OpenStreetMap contributors",
	}}

	err := app.writeJSON(w, http.StatusOK, env, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
