package main

import (
	"context"
	"errors"
	"net/http"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/menu"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// The translateMenuHandler translates the text the app read from a menu
// photo. Every failure is a 503: the app then uses its on-device translator,
// so the traveller still gets something.
func (app *application) translateMenuHandler(w http.ResponseWriter, r *http.Request) {
	var input data.MenuRequest

	err := app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	v := validator.New()

	if data.ValidateMenuRequest(v, &input); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	if app.menu == nil {
		app.serviceUnavailableResponse(w, r, "the menu translator is not available because no AI service is configured")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), app.config.replyTimeout())
	defer cancel()

	lines, err := app.menu.Translate(ctx, input)
	if err != nil {
		app.logError(r, err)
		switch {
		case errors.Is(err, menu.ErrBadOutput):
			app.serviceUnavailableResponse(w, r, "the menu translator gave an unusable answer, please try again")
		default:
			app.serviceUnavailableResponse(w, r, "the menu translator is busy right now, please try again in a minute")
		}
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"menu": map[string]any{"lines": lines}}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
