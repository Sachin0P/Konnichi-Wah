package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/planner"
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

// HotelRecommendation represents one AI-searched hotel recommendation for a city.
type HotelRecommendation struct {
	City        string `json:"city"`
	HotelName   string `json:"hotel_name"`
	HotelPrice  string `json:"hotel_price"`
	HotelReason string `json:"hotel_reason"`
}

func (app *application) recommendHotelsHandler(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Cities      []string `json:"cities"`
		BudgetBand  string   `json:"budget_band"`
		GroupType   string   `json:"group_type"`
		Notes       string   `json:"notes"`
		ItineraryID int64    `json:"itinerary_id"`
	}

	err := app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	if len(input.Cities) == 0 && input.ItineraryID > 0 {
		itin, err := app.models.Itineraries.Get(input.ItineraryID)
		if err == nil && itin != nil {
			citySet := make(map[string]bool)
			for _, d := range itin.Days {
				if d.City != "" && !citySet[strings.ToLower(d.City)] {
					citySet[strings.ToLower(d.City)] = true
					input.Cities = append(input.Cities, d.City)
				}
			}
		}
	}

	if len(input.Cities) == 0 {
		input.Cities = []string{"Tokyo"}
	}
	if input.BudgetBand == "" {
		input.BudgetBand = "2-3L"
	}
	if input.GroupType == "" {
		input.GroupType = "couple"
	}

	hotels := app.searchHotels(r.Context(), input.Cities, input.BudgetBand, input.GroupType, input.Notes)

	if input.ItineraryID > 0 {
		itin, err := app.models.Itineraries.Get(input.ItineraryID)
		if err == nil && itin != nil {
			hotelMap := make(map[string]HotelRecommendation)
			for _, h := range hotels {
				hotelMap[strings.ToLower(strings.TrimSpace(h.City))] = h
			}
			updated := false
			for i := range itin.Days {
				normCity := strings.ToLower(strings.TrimSpace(itin.Days[i].City))
				if h, ok := hotelMap[normCity]; ok {
					itin.Days[i].HotelName = h.HotelName
					itin.Days[i].HotelPrice = h.HotelPrice
					itin.Days[i].HotelReason = h.HotelReason
					updated = true
				}
			}
			if updated {
				_ = app.models.Itineraries.Update(itin)
			}
		}
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"hotels": hotels}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}

func (app *application) searchHotels(ctx context.Context, cities []string, budgetBand, groupType, notes string) []HotelRecommendation {
	hotels := make([]HotelRecommendation, 0, len(cities))

	for _, city := range cities {
		hotelTier := "budget hotel near station under 8000 yen"
		priceEstimate := "~¥7,500/night (~₹4,200)"
		switch budgetBand {
		case "2-3L":
			hotelTier = "4 star hotel onsen near station 15000 yen"
			priceEstimate = "~¥15,000/night (~₹8,400)"
		case "3L+":
			hotelTier = "luxury 5 star hotel authentic ryokan onsen"
			priceEstimate = "~¥42,000/night (~₹23,500)"
		}
		if groupType == "family_kids" || groupType == "family_elders" {
			hotelTier += " family friendly elevator"
		}

		query := fmt.Sprintf("best %s %s Japan", hotelTier, city)
		searchCtx, cancel := context.WithTimeout(ctx, 4*time.Second)
		results, err := app.websearch.Search(searchCtx, query, 3)
		cancel()

		var hotel HotelRecommendation
		hotel.City = city
		hotel.HotelPrice = priceEstimate

		if err == nil && len(results) > 0 {
			// If planner has LLM Generator, ask it to pick the hotel based on search findings
			if llmPlanner, ok := app.planner.(planner.LLM); ok && llmPlanner.Client != nil {
				var findings strings.Builder
				for _, r := range results {
					findings.WriteString(fmt.Sprintf("- %s: %s\n", r.Title, r.Snippet))
				}
				prompt := fmt.Sprintf("You are the hotel advisor for Konnichi-Wah!\nLive web search findings for hotels in %s:\n%s\nTraveller party: %s, Notes: %s, Budget: %s (in-Japan ground spend, excluding flights).\nPick ONE real hotel or ryokan in %s from the search results matching their requirements.",
					city, findings.String(), groupType, notes, budgetBand, city)

				schema := map[string]any{
					"type": "object",
					"properties": map[string]any{
						"hotel_name":   map[string]any{"type": "string"},
						"hotel_price":  map[string]any{"type": "string"},
						"hotel_reason": map[string]any{"type": "string"},
					},
					"required":             []string{"hotel_name", "hotel_price", "hotel_reason"},
					"additionalProperties": false,
				}

				llmCtx, cancelLLM := context.WithTimeout(ctx, 6*time.Second)
				js, llmErr := llmPlanner.Client.GenerateJSON(llmCtx, "You recommend one real hotel in Japan from search results.", prompt, schema)
				cancelLLM()

				if llmErr == nil {
					var out struct {
						HotelName   string `json:"hotel_name"`
						HotelPrice  string `json:"hotel_price"`
						HotelReason string `json:"hotel_reason"`
					}
					if json.Unmarshal(js, &out) == nil && out.HotelName != "" {
						hotel.HotelName = out.HotelName
						if out.HotelPrice != "" {
							hotel.HotelPrice = out.HotelPrice
						}
						hotel.HotelReason = out.HotelReason
					}
				}
			}

			// If LLM didn't set it, extract from top search result
			if hotel.HotelName == "" {
				top := results[0]
				title := top.Title
				// Clean search engine suffixes (e.g. "- Booking.com", "| Agoda")
				if idx := strings.Index(title, " - "); idx > 0 {
					title = title[:idx]
				} else if idx := strings.Index(title, " | "); idx > 0 {
					title = title[:idx]
				}
				hotel.HotelName = strings.TrimSpace(title)
				hotel.HotelReason = strings.TrimSpace(top.Snippet)
				if len(hotel.HotelReason) > 200 {
					hotel.HotelReason = hotel.HotelReason[:200]
				}
			}
		}

		if hotel.HotelName == "" {
			hotel.HotelName = fmt.Sprintf("Central %s Station Area Hotel", city)
			hotel.HotelReason = "Convenient hotel located right by transit connections, within your ground budget."
		}

		hotels = append(hotels, hotel)
	}

	return hotels
}
