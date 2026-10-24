package planner

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/websearch"
)

// ErrInvalidOutput is returned when the LLM's reply doesn't pass our checks.
var ErrInvalidOutput = errors.New("planner: invalid output from llm")

// Generator is the part of an LLM client the planner needs: the Gemini client
// and the OpenAI-compatible one (Groq, or a local Ollama server) both have
// it. Depending on this small interface lets the tests use a fake.
type Generator interface {
	GenerateJSON(ctx context.Context, instructions, input string, schema any) ([]byte, error)
}

// LLM is a Planner that asks an LLM for suggestions.
type LLM struct {
	Client   Generator
	Searcher websearch.Searcher
}

func (g LLM) Suggest(ctx context.Context, req data.TripRequest) (*data.Plan, error) {
	profile, err := json.MarshalIndent(req.Profile, "", "  ")
	if err != nil {
		return nil, err
	}

	notes := strings.TrimSpace(req.Profile.Notes)
	notesHeader := ""
	if notes != "" {
		notesHeader = fmt.Sprintf("CRITICAL USER REQUESTS (HIGHEST PRIORITY):\n\"%s\"\nYou MUST customize the trip specifically to satisfy these requests! If specific places, characters, shopping, accessibility, or walking limits are mentioned, include them in the suggestions.\n\n", notes)
	}

	alcoholNote := ""
	if req.Profile.DrinksAlcohol != nil {
		if *req.Profile.DrinksAlcohol {
			alcoholNote = "ALCOHOL PREFERENCE: Drinks alcohol. Suggest lively izakayas, craft beer pubs, standing bars (tachinomi), or famous nightlife alleys like Golden Gai, Nonbei Yokocho, or Pontocho.\n\n"
		} else {
			alcoholNote = "ALCOHOL PREFERENCE: Does NOT drink alcohol. Strictly avoid pubs, bars, and izakayas. Suggest tea houses, kissaten coffee shops, dessert cafes, or evening viewpoints instead.\n\n"
		}
	}

	var webFindings strings.Builder
	if g.Searcher != nil {
		queries := make([]string, 0, 2)
		if notes != "" {
			queries = append(queries, fmt.Sprintf("%s Japan travel best locations", notes))
		}
		if len(req.Profile.Interests) > 0 {
			interestsStr := strings.Join(req.Profile.Interests, " ")
			queries = append(queries, fmt.Sprintf("best places in Tokyo Kyoto Osaka for %s", interestsStr))
		} else if req.Profile.DrinksAlcohol != nil && *req.Profile.DrinksAlcohol {
			queries = append(queries, "best lively izakaya craft beer bar nightlife Tokyo Osaka")
		}

		searchCtx, cancelSearch := context.WithTimeout(ctx, 4*time.Second)
		defer cancelSearch()

		var allResults []websearch.Result
		for _, q := range queries {
			if res, err := g.Searcher.Search(searchCtx, q, 3); err == nil && len(res) > 0 {
				allResults = append(allResults, res...)
			}
		}

		if len(allResults) > 0 {
			webFindings.WriteString("LIVE WEB SEARCH FINDINGS (REAL-TIME TRAVEL GROUNDING):\n")
			for _, r := range allResults {
				webFindings.WriteString(fmt.Sprintf("- %s: %s (Source: %s)\n", r.Title, r.Snippet, r.URL))
			}
			webFindings.WriteString("INSTRUCTION: Use these fresh web search findings to inspire your recommendations and personalize the locations according to the user's preferences!\n\n")
		}
	}

	input := fmt.Sprintf("%s%s%sTrip length: %d days\nTravel month: %s\nPace: %s\nTraveller profile (JSON):\n%s",
		notesHeader, alcoholNote, webFindings.String(), req.TripDays, time.Month(req.TravelMonth), req.Profile.Pace, profile)

	js, err := g.Client.GenerateJSON(ctx, plannerInstructions, input, planSchema)
	if err != nil {
		return nil, err
	}

	// Decode strictly. The schema should already stop unknown keys, but we
	// don't rely on the LLM provider to enforce it.
	var output struct {
		Cities      []data.CityPlan   `json:"cities"`
		Suggestions []data.Suggestion `json:"suggestions"`
	}

	dec := json.NewDecoder(bytes.NewReader(js))
	dec.DisallowUnknownFields()

	err = dec.Decode(&output)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrInvalidOutput, err)
	}

	// Sanitize and adjust city days if slightly off (e.g. +/- 1 day, common with local LLMs)
	if len(output.Cities) > 0 {
		totalDays := 0
		for _, c := range output.Cities {
			totalDays += c.Days
		}
		if diff := req.TripDays - totalDays; diff == 1 || diff == -1 {
			maxIdx := 0
			for i := 1; i < len(output.Cities); i++ {
				if output.Cities[i].Days > output.Cities[maxIdx].Days {
					maxIdx = i
				}
			}
			output.Cities[maxIdx].Days += diff
			if output.Cities[maxIdx].Days < 1 {
				output.Cities[maxIdx].Days = 1
			}
		}
	}

	plan := &data.Plan{
		Cities:      output.Cities,
		Suggestions: []data.Suggestion{},
		Source:      "llm",
	}

	// Keep only the places and activities. The schema already forbids food,
	// but if any slips through we drop it rather than show a user an
	// unchecked restaurant. We assign the ids ourselves so they're unique.
	for _, s := range output.Suggestions {
		k := strings.ToLower(strings.TrimSpace(s.Kind))
		pt := strings.ToLower(strings.TrimSpace(s.PlaceType))
		if k == "food" || pt == "restaurant" {
			continue
		}

		if k == "activity" || k == "experience" {
			s.Kind = "activity"
		} else {
			s.Kind = "place"
		}

		s.PlaceType = normalizePlaceType(pt)

		if len(s.Name) > 100 {
			s.Name = s.Name[:100]
		}
		if len(s.City) > 50 {
			s.City = s.City[:50]
		}
		if len(s.Reason) > 300 {
			s.Reason = s.Reason[:300]
		}

		s.ID = fmt.Sprintf("s%d", len(plan.Suggestions)+1)
		plan.Suggestions = append(plan.Suggestions, s)
	}

	// Add food from our diet-checked catalogue for the cities the LLM chose.
	// Their ids are catalogue slugs, so they can't clash with "s1", "s2"...
	plan.Suggestions = append(plan.Suggestions, foodFor(plan.Cities, req.Profile)...)

	v := validator.New()

	if data.ValidatePlan(v, plan, req.TripDays); !v.Valid() {
		return nil, fmt.Errorf("%w: %v", ErrInvalidOutput, v.Errors)
	}

	return plan, nil
}

func normalizePlaceType(t string) string {
	clean := strings.ToLower(strings.TrimSpace(t))
	clean = strings.ReplaceAll(clean, " ", "_")
	clean = strings.ReplaceAll(clean, "-", "_")

	switch clean {
	case "temple", "buddhist_temple":
		return "temple"
	case "shrine", "shinto_shrine":
		return "shrine"
	case "onsen", "hot_spring", "bathhouse", "sento":
		return "onsen"
	case "ryokan", "inn", "hotel", "stay":
		return "ryokan"
	case "park", "garden", "nature", "botanical_garden":
		return "park"
	case "street", "neighborhood", "district", "quarter", "alley", "area":
		return "street"
	case "shop", "market", "bazaar", "shopping", "mall", "store":
		return "shop"
	case "attraction", "museum", "castle", "tower", "viewpoint", "theme_park", "entertainment", "sight", "monument":
		return "attraction"
	default:
		if slices.Contains(llmPlaceTypes, clean) {
			return clean
		}
		return clean
	}
}

// llmPlaceTypes are the place types the LLM may use: every type in our
// rulebook except restaurant (food comes from the catalogue) and train and
// taxi (those are ways to travel, not things to suggest).
var llmPlaceTypes = slices.DeleteFunc(slices.Clone(data.PlaceTypes), func(t string) bool {
	return t == "restaurant" || t == "train" || t == "taxi"
})

// planSchema is the JSON schema the LLM's reply must follow. The enums come
// from the same lists we validate against, so the LLM can only use place types
// that exist in our etiquette rulebook. The model doesn't enforce length
// limits like maxLength, which is one reason ValidatePlan still runs on its
// output. We cap suggestions at 25, leaving room under ValidatePlan's limit of
// 40 for the catalogue food we add afterwards.
var planSchema = map[string]any{
	"type": "object",
	"properties": map[string]any{
		"cities": map[string]any{
			"type":     "array",
			"minItems": 1,
			"maxItems": 6,
			"items": map[string]any{
				"type": "object",
				"properties": map[string]any{
					"name":   map[string]any{"type": "string"},
					"days":   map[string]any{"type": "integer", "minimum": 1},
					"reason": map[string]any{"type": "string"},
				},
				"required":             []string{"name", "days", "reason"},
				"additionalProperties": false,
			},
		},
		"suggestions": map[string]any{
			"type":     "array",
			"minItems": 1,
			"maxItems": 25,
			"items": map[string]any{
				"type": "object",
				"properties": map[string]any{
					"kind":       map[string]any{"type": "string", "enum": []string{"place", "activity"}},
					"name":       map[string]any{"type": "string"},
					"city":       map[string]any{"type": "string"},
					"place_type": map[string]any{"type": "string", "enum": llmPlaceTypes},
					"reason":     map[string]any{"type": "string"},
				},
				"required":             []string{"kind", "name", "city", "place_type", "reason"},
				"additionalProperties": false,
			},
		},
	},
	"required":             []string{"cities", "suggestions"},
	"additionalProperties": false,
}

// plannerInstructions is the system prompt. It tells the model to leave out
// anything we get from verified sources instead (routes, etiquette, addresses),
// because our design specifications require verified data sources.
const plannerInstructions = `You are the trip planner for Konnichi-Wah!, an app that helps Indian travellers plan their first trip to Japan.

Given a trip request, decide which cities to visit and for how many days, then suggest places and activities the traveller can choose from.

CRITICAL RULES:
1. USER SPECIAL REQUESTS & NOTES (HIGHEST PRIORITY):
   - If the traveller provided notes or special requests (such as specific places, characters, anime, nature, shopping, walking limits, or dietary preferences), you MUST prioritize satisfying those requests first!
   - Do NOT just output the generic top tourist sights if the user asked for specific themes or places. Incorporate their requested spots or the closest real-world equivalents.

2. PACE & SUGGESTION COUNT:
   - "relaxed" pace: The traveller wants a gentle tempo with fewer stops and more rest. Give 2 to 3 suggestions per day (total 6 to 12 suggestions). Do not overwhelm them.
   - "balanced" pace: Give 3 to 4 suggestions per day (total 10 to 18 suggestions).
   - "packed" pace: The traveller wants to see as much as possible from morning till night. Give 4 to 5 suggestions per day (total 14 to 25 suggestions).
   - Never give more than 25 suggestions in total.

3. SCHEMA & ENUMS (STRICT):
   - The days for all cities must add up exactly to the trip length.
   - Only suggest real, well-known places that exist today, using their common English names.
   - Each suggestion's city must be one of your cities, or a day trip from one (e.g. Nara from Kyoto, Hakone from Tokyo).
   - kind: MUST be either "place" (for a sight, temple, park, shop) or "activity" (for an experience, workshop, boat ride, tour). Do NOT use any other string.
   - place_type: MUST be one of:
     * "temple" (Buddhist temples)
     * "shrine" (Shinto shrines)
     * "onsen" (hot springs and bathhouses)
     * "ryokan" (traditional Japanese inns)
     * "park" (gardens, nature, parks)
     * "street" (historic streets, lively districts, walking areas)
     * "shop" (markets, department stores, anime/manga shops, souvenir areas)
     * "attraction" (castles, museums, observation decks, towers, theme parks, viewpoints)
     (Use "attraction" for castles, museums, viewpoints; use "park" for gardens; use "shop" for markets).
   - Do not suggest restaurants or specific places to eat. The app adds food options it has checked against the traveller's diet. Food markets are fine as a shop if the traveller likes food.
   - Match the interests, budget band, pace, and travelling group (family_kids: child-friendly; family_elders: little walking, few stairs, frequent rest).
   - Each reason is one short sentence, under 200 characters, explaining why it suits this specific traveller.
   - Do not include train routes, travel times, prices, opening hours, addresses or etiquette tips. The app adds those from verified sources.

4. NIGHTLIFE, PUBS & YOUNGER TRAVELERS:
   - For young travellers (solo, couples, friends) or those who drink alcohol / enjoy nightlife, suggest lively izakaya streets, craft beer hubs, retro arcades, and famous bar alleys (e.g. Shinjuku Golden Gai, Shibuya Nonbei Yokocho, Omoide Yokocho, Pontocho, Ura-Namba). Use "street" for nightlife alleys/areas or "shop" for markets/arcades.
   - If the profile says no alcohol, avoid bar alleys and suggest night viewpoints (Shibuya Sky, Tokyo Skytree, Roppongi Hills), evening tea/dessert spots, and illuminated temples.`
