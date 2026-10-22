package main

import (
	"net/http"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// The listEtiquetteRulesHandler returns the rules for one place type, like
// GET /v1/etiquette?place_type=temple. The place_type parameter is optional;
// without it we return the whole rulebook, which the app can use to refresh
// its offline copy.
func (app *application) listEtiquetteRulesHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		PlaceType string
	}

	v := validator.New()

	qs := r.URL.Query()

	input.PlaceType = app.readString(qs, "place_type", "")

	// List the permitted values in the message, so whoever is building the app
	// can see straight away what the valid options are.
	if input.PlaceType != "" {
		v.Check(validator.PermittedValue(input.PlaceType, data.PlaceTypes...), "place_type", "must be one of: "+strings.Join(data.PlaceTypes, ", "))
	}

	if !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	rules, err := app.models.EtiquetteRules.GetAll(input.PlaceType)
	if err != nil {
		app.serverErrorResponse(w, r, err)
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"etiquette_rules": rules}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
