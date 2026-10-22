package planner

import (
	"context"
	"slices"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

func newRequest(diet string, avoid []string, interests []string, days, month int) data.TripRequest {
	return data.TripRequest{
		Profile: data.UserProfile{
			Interests:     interests,
			BudgetBand:    "2-3L",
			Pace:          "balanced",
			GroupType:     "couple",
			Diet:          diet,
			Avoid:         avoid,
			SpiceLevel:    "medium",
			JapaneseLevel: "none",
		},
		TripDays:    days,
		TravelMonth: month,
	}
}

// The catalogue is also our fallback for bad LLM output, so it must produce a
// valid plan for every trip length, every month and every single interest.
func TestCatalogueAlwaysValid(t *testing.T) {
	for _, interest := range data.Interests {
		for days := 1; days <= 30; days++ {
			for month := 1; month <= 12; month++ {
				req := newRequest("jain", nil, []string{interest}, days, month)

				plan, err := Catalogue{}.Suggest(context.Background(), req)
				if err != nil {
					t.Fatal(err)
				}

				v := validator.New()
				if data.ValidatePlan(v, plan, days); !v.Valid() {
					t.Fatalf("interest=%s days=%d month=%d: %v", interest, days, month, v.Errors)
				}
			}
		}
	}
}

func TestCatalogueFood(t *testing.T) {
	tests := []struct {
		name    string
		diet    string
		avoid   []string
		want    []string
		notWant []string
	}{
		{
			name:    "Veg",
			diet:    "veg",
			want:    []string{"tokyo-ts-tantan", "tokyo-nirvanam", "tokyo-coco-ichibanya"},
			notWant: []string{"tokyo-ichiran", "tokyo-kura-sushi", "tokyo-naritaya", "osaka-okonomiyaki"},
		},
		{
			name:    "Jain",
			diet:    "jain",
			want:    []string{"tokyo-nirvanam", "kyoto-shigetsu"},
			notWant: []string{"tokyo-ts-tantan", "tokyo-coco-ichibanya"},
		},
		{
			name:    "Halal",
			diet:    "halal",
			want:    []string{"tokyo-naritaya", "tokyo-kura-sushi"},
			notWant: []string{"tokyo-ichiran"},
		},
		{
			name:    "Non-veg avoiding pork",
			diet:    "non_veg",
			avoid:   []string{"pork"},
			want:    []string{"tokyo-kura-sushi", "tokyo-naritaya"},
			notWant: []string{"tokyo-ichiran"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := newRequest(tt.diet, tt.avoid, []string{"food"}, 7, 4)

			plan, err := Catalogue{}.Suggest(context.Background(), req)
			if err != nil {
				t.Fatal(err)
			}

			var ids []string
			for _, s := range plan.Suggestions {
				ids = append(ids, s.ID)
			}

			for _, id := range tt.want {
				if !slices.Contains(ids, id) {
					t.Errorf("want %q in suggestions", id)
				}
			}
			for _, id := range tt.notWant {
				if slices.Contains(ids, id) {
					t.Errorf("did not want %q in suggestions", id)
				}
			}
		})
	}
}

func TestAllocateCities(t *testing.T) {
	tests := []struct {
		name      string
		interests []string
		days      int
		month     int
		want      []string
	}{
		{name: "Short trip", interests: []string{"food"}, days: 3, month: 4, want: []string{"Tokyo"}},
		{name: "Medium trip", interests: []string{"food"}, days: 5, month: 4, want: []string{"Tokyo", "Kyoto"}},
		{name: "Long trip", interests: []string{"food"}, days: 10, month: 4, want: []string{"Tokyo", "Kyoto", "Osaka"}},
		{name: "Snow in winter", interests: []string{"snow"}, days: 10, month: 1, want: []string{"Tokyo", "Kyoto", "Osaka", "Sapporo"}},
		{name: "Snow in summer", interests: []string{"snow"}, days: 10, month: 7, want: []string{"Tokyo", "Kyoto", "Osaka"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cities := allocateCities(newRequest("veg", nil, tt.interests, tt.days, tt.month))

			var names []string
			for _, c := range cities {
				names = append(names, c.Name)
			}

			if !slices.Equal(names, tt.want) {
				t.Errorf("got %v; want %v", names, tt.want)
			}
		})
	}
}
