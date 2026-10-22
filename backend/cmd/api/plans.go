package main

import (
	"fmt"
	"net/http"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// The createPlanSuggestionsHandler takes the questionnaire answers and returns
// suggested cities, places, activities and food for the user to pick from.
// Nothing is stored yet, so we respond with 200 OK rather than 201 Created.
func (app *application) createPlanSuggestionsHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Profile     data.UserProfile `json:"profile"`
		TripDays    int              `json:"trip_days"`
		TravelMonth int              `json:"travel_month"`
	}

	err := app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	req := data.TripRequest{
		Profile:     input.Profile,
		TripDays:    input.TripDays,
		TravelMonth: input.TravelMonth,
	}

	v := validator.New()

	if data.ValidateTripRequest(v, &req); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	// Pass the request context so that, once we call a real LLM, the call is
	// cancelled if the client disconnects.
	plan, err := app.planner.Suggest(r.Context(), req)
	if err != nil {
		app.serverErrorResponse(w, r, err)
		return
	}

	// Check the planner's output before it goes to the app. This is the final
	// gate, whatever planner produced the plan. If it fails here, it's a bug on
	// our side, not the client's, so we log the details and send a 500.
	v = validator.New()

	if data.ValidatePlan(v, plan, req.TripDays); !v.Valid() {
		app.serverErrorResponse(w, r, fmt.Errorf("invalid plan from %s planner: %v", plan.Source, v.Errors))
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"plan": plan}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
