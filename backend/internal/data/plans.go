package data

import (
	"fmt"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// SuggestionKinds lists the kinds of suggestion card the app can show.
var SuggestionKinds = []string{"place", "activity", "food"}

// TripRequest is everything the planner needs: the traveller's profile plus the
// details of this particular trip.
type TripRequest struct {
	Profile     UserProfile `json:"profile"`
	TripDays    int         `json:"trip_days"`
	TravelMonth int         `json:"travel_month"`
}

func ValidateTripRequest(v *validator.Validator, req *TripRequest) {
	ValidateUserProfile(v, &req.Profile)

	v.Check(req.TripDays >= 1, "trip_days", "must be at least 1")
	v.Check(req.TripDays <= 30, "trip_days", "must not be more than 30")

	v.Check(req.TravelMonth >= 1 && req.TravelMonth <= 12, "travel_month", "must be between 1 and 12")
}

// CityPlan is one city in the suggested route, with how many days to spend
// there and a verified hotel stay recommendation tailored to the traveller's budget.
type CityPlan struct {
	Name        string `json:"name"`
	Days        int    `json:"days"`
	Reason      string `json:"reason"`
	HotelName   string `json:"hotel_name,omitempty"`
	HotelArea   string `json:"hotel_area,omitempty"`
	HotelPrice  string `json:"hotel_price,omitempty"`
	HotelReason string `json:"hotel_reason,omitempty"`
}

// Suggestion is one selectable card: a place to visit, an activity or
// somewhere to eat. Coordinates and Google place ids are deliberately not
// included; an LLM would make them up. We'll look them up from Google Places
// when the user turns their chosen suggestions into an itinerary.
type Suggestion struct {
	ID        string `json:"id"`
	Kind      string `json:"kind"`
	Name      string `json:"name"`
	City      string `json:"city"`
	PlaceType string `json:"place_type"`
	Reason    string `json:"reason"`
}

// Plan is the planner's full response. Source records where it came from
// ("catalogue" for our built-in suggestions, or later "llm"), which helps
// when debugging the demo.
type Plan struct {
	Cities      []CityPlan   `json:"cities"`
	Suggestions []Suggestion `json:"suggestions"`
	Source      string       `json:"source"`
}

// ValidatePlan checks a planner response before it goes to the app. This is
// the gate that stops bad LLM output from ever reaching a user, so it is
// deliberately strict: every place type must come from our rulebook, and the
// city days must add up to the trip length.
func ValidatePlan(v *validator.Validator, plan *Plan, tripDays int) {
	v.Check(len(plan.Cities) >= 1, "cities", "must contain at least 1 city")

	totalDays := 0
	for i, city := range plan.Cities {
		key := fmt.Sprintf("cities[%d]", i)

		v.Check(city.Name != "", key+".name", "must be provided")
		v.Check(len(city.Name) <= 50, key+".name", "must not be more than 50 bytes long")
		v.Check(city.Days >= 1, key+".days", "must be at least 1")
		v.Check(len(city.Reason) <= 300, key+".reason", "must not be more than 300 bytes long")
		if city.HotelName != "" {
			v.Check(len(city.HotelName) <= 100, key+".hotel_name", "must not be more than 100 bytes long")
			v.Check(len(city.HotelArea) <= 100, key+".hotel_area", "must not be more than 100 bytes long")
			v.Check(len(city.HotelPrice) <= 100, key+".hotel_price", "must not be more than 100 bytes long")
			v.Check(len(city.HotelReason) <= 300, key+".hotel_reason", "must not be more than 300 bytes long")
		}

		totalDays += city.Days
	}
	v.Check(totalDays == tripDays, "cities", fmt.Sprintf("days must add up to %d", tripDays))

	v.Check(len(plan.Suggestions) >= 1, "suggestions", "must contain at least 1 suggestion")
	v.Check(len(plan.Suggestions) <= 40, "suggestions", "must not contain more than 40 suggestions")

	ids := make([]string, len(plan.Suggestions))
	for i, s := range plan.Suggestions {
		key := fmt.Sprintf("suggestions[%d]", i)

		v.Check(s.ID != "", key+".id", "must be provided")
		v.Check(validator.PermittedValue(s.Kind, SuggestionKinds...), key+".kind", mustBeOneOf(SuggestionKinds))
		v.Check(s.Name != "", key+".name", "must be provided")
		v.Check(len(s.Name) <= 100, key+".name", "must not be more than 100 bytes long")
		v.Check(s.City != "", key+".city", "must be provided")
		v.Check(len(s.City) <= 50, key+".city", "must not be more than 50 bytes long")
		v.Check(validator.PermittedValue(s.PlaceType, PlaceTypes...), key+".place_type", mustBeOneOf(PlaceTypes))
		v.Check(s.Reason != "", key+".reason", "must be provided")
		v.Check(len(s.Reason) <= 300, key+".reason", "must not be more than 300 bytes long")

		ids[i] = s.ID
	}
	v.Check(validator.Unique(ids), "suggestions", "must not contain duplicate ids")
}
