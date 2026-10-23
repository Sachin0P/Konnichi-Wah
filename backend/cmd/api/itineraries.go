package main

import (
	"crypto/rand"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/planner"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// maxTipsPerStop limits how many etiquette tips are attached to each stop.
// The rules come back important-first, so these are the ones that matter most.
const maxTipsPerStop = 5

// The createItineraryHandler turns the cities and the suggestions the user
// picked into a day-by-day itinerary, and saves it.
func (app *application) createItineraryHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Title       string            `json:"title"`
		StartDate   *string           `json:"start_date"`
		Cities      []data.CityPlan   `json:"cities"`
		Suggestions []data.Suggestion `json:"suggestions"`
	}

	err := app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	// The cities and suggestions come straight from a plan the app received,
	// so we check them with the same rules as the planner's output. The trip
	// length is whatever the cities add up to.
	totalDays := 0
	for _, c := range input.Cities {
		totalDays += c.Days
	}

	v := validator.New()

	if data.ValidatePlan(v, &data.Plan{Cities: input.Cities, Suggestions: input.Suggestions}, totalDays); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	itinerary := &data.Itinerary{
		Title:     strings.TrimSpace(input.Title),
		StartDate: input.StartDate,
		Days:      planner.Arrange(input.Cities, input.Suggestions, newStopID),
	}

	if itinerary.Title == "" {
		itinerary.Title = fmt.Sprintf("My %d-day Japan trip", totalDays)
	}

	if data.ValidateItinerary(v, itinerary); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	err = app.attachTips(itinerary.Days)
	if err != nil {
		app.serverErrorResponse(w, r, err)
		return
	}

	err = app.models.Itineraries.Insert(itinerary)
	if err != nil {
		app.serverErrorResponse(w, r, err)
		return
	}

	// Include a Location header with the URL of the new itinerary.
	headers := make(http.Header)
	headers.Set("Location", fmt.Sprintf("/v1/itineraries/%d", itinerary.ID))

	err = app.writeJSON(w, http.StatusCreated, envelope{"itinerary": itinerary}, headers)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

func (app *application) showItineraryHandler(w http.ResponseWriter, r *http.Request) {
	id, err := app.readIDParam(r)
	if err != nil {
		app.notFoundResponse(w, r)
		return
	}

	itinerary, err := app.models.Itineraries.Get(id)
	if err != nil {
		switch {
		case errors.Is(err, data.ErrRecordNotFound):
			app.notFoundResponse(w, r)
		default:
			app.serverErrorResponse(w, r, err)
		}
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"itinerary": itinerary}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

// The updateItineraryHandler applies a partial update. The app usually sends
// the whole "days" list after the user reorders, deletes or adds stops.
func (app *application) updateItineraryHandler(w http.ResponseWriter, r *http.Request) {
	id, err := app.readIDParam(r)
	if err != nil {
		app.notFoundResponse(w, r)
		return
	}

	itinerary, err := app.models.Itineraries.Get(id)
	if err != nil {
		switch {
		case errors.Is(err, data.ErrRecordNotFound):
			app.notFoundResponse(w, r)
		default:
			app.serverErrorResponse(w, r, err)
		}
		return
	}

	// Use pointers (and a nil-able slice) so we can tell a field that wasn't
	// sent apart from one sent as its zero value. An empty start_date string
	// clears the date.
	var input struct {
		Title     *string    `json:"title"`
		StartDate *string    `json:"start_date"`
		Days      []data.Day `json:"days"`
	}

	err = app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	if input.Title != nil {
		itinerary.Title = strings.TrimSpace(*input.Title)
	}
	if input.StartDate != nil {
		if *input.StartDate == "" {
			itinerary.StartDate = nil
		} else {
			itinerary.StartDate = input.StartDate
		}
	}
	if input.Days != nil {
		itinerary.Days = input.Days
	}

	// Number the days by position, rather than trusting the client's indexes.
	for i := range itinerary.Days {
		itinerary.Days[i].Index = i + 1
	}

	v := validator.New()

	if data.ValidateItinerary(v, itinerary); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	// Recalculate tips for every stop, including ones the user just added.
	// Any tip_ids the client sent are replaced, so tips only ever come from
	// our rulebook.
	err = app.attachTips(itinerary.Days)
	if err != nil {
		app.serverErrorResponse(w, r, err)
		return
	}

	err = app.models.Itineraries.Update(itinerary)
	if err != nil {
		switch {
		case errors.Is(err, data.ErrEditConflict):
			app.editConflictResponse(w, r)
		default:
			app.serverErrorResponse(w, r, err)
		}
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"itinerary": itinerary}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

// attachTips sets each stop's TipIDs to the most important etiquette rules
// for its place type. It looks each place type up once per request.
func (app *application) attachTips(days []data.Day) error {
	tipsByType := make(map[string][]string)

	for i := range days {
		for j := range days[i].Stops {
			stop := &days[i].Stops[j]

			tips, ok := tipsByType[stop.PlaceType]
			if !ok {
				rules, err := app.models.EtiquetteRules.GetAll(stop.PlaceType)
				if err != nil {
					return err
				}

				tips = []string{}
				for _, rule := range rules[:min(maxTipsPerStop, len(rules))] {
					tips = append(tips, rule.ID)
				}
				tipsByType[stop.PlaceType] = tips
			}

			stop.TipIDs = tips
		}
	}

	return nil
}

// newStopID returns a random id for a new stop, like "st_k3x9...". The app
// generates its own ids for stops added offline, so these only need to be
// unique, not sequential.
func newStopID() string {
	return "st_" + strings.ToLower(rand.Text())
}
