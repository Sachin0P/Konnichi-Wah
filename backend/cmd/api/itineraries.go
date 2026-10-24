package main

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/planner"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
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

	app.locateStops(r.Context(), itinerary.Days)

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

	// Look up coordinates for stops the user just added (the others already
	// have them, so this is usually instant).
	app.locateStops(r.Context(), itinerary.Days)

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
// for its place type that apply in its city. It looks each place type up once
// per request. The app does the same lookup offline (data/Tips.kt), so keep
// the two in step.
func (app *application) attachTips(days []data.Day) error {
	rulesByType := make(map[string][]*data.EtiquetteRule)

	for i := range days {
		for j := range days[i].Stops {
			stop := &days[i].Stops[j]

			rules, ok := rulesByType[stop.PlaceType]
			if !ok {
				var err error
				rules, err = app.models.EtiquetteRules.GetAll(stop.PlaceType)
				if err != nil {
					return err
				}
				rulesByType[stop.PlaceType] = rules
			}

			stop.TipIDs = []string{}
			for _, rule := range rules {
				if len(stop.TipIDs) == maxTipsPerStop {
					break
				}
				if rule.AppliesIn(stop.City) {
					stop.TipIDs = append(stop.TipIDs, rule.ID)
				}
			}
		}
	}

	return nil
}

// locateTimeout caps how long creating or updating an itinerary may spend
// looking up stop coordinates.
const locateTimeout = 10 * time.Second

// locateStops fills in Lat and Lng for stops that don't have them yet, by
// geocoding each stop's name near its city. Searches like "Find food nearby"
// can then use the stored coordinates instead of looking the name up again,
// so they keep working even if the geocoder is unreachable later. A stop that
// can't be found (or runs out of time) is simply left without coordinates;
// it's never an error.
func (app *application) locateStops(ctx context.Context, days []data.Day) {
	ctx, cancel := context.WithTimeout(ctx, locateTimeout)
	defer cancel()

	var wg sync.WaitGroup
	// Only a few lookups at once: Transitous is a volunteer service.
	limit := make(chan struct{}, 4)

	for i := range days {
		for j := range days[i].Stops {
			stop := &days[i].Stops[j]
			if stop.Lat != nil && stop.Lng != nil {
				continue
			}

			wg.Add(1)
			go func() {
				defer wg.Done()
				select {
				case limit <- struct{}{}:
				case <-ctx.Done():
					return
				}
				defer func() { <-limit }()

				place, err := app.transit.GeocodeExact(ctx, stop.PlaceName, transit.CityCentre(stop.City))
				if err != nil {
					return
				}
				lat, lng := place.Location.Lat, place.Location.Lng
				stop.Lat, stop.Lng = &lat, &lng
			}()
		}
	}

	wg.Wait()
}

// newStopID returns a random id for a new stop, like "st_k3x9...". The app
// generates its own ids for stops added offline, so these only need to be
// unique, not sequential.
func newStopID() string {
	return "st_" + strings.ToLower(rand.Text())
}
