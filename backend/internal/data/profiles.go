package data

import (
	"fmt"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// These lists hold the permitted answers for each questionnaire question. The
// Android app sends these exact strings, so changing one here means changing it
// in the app too.
var (
	Interests      = []string{"temples_shrines", "anime", "food", "nature", "shopping", "snow", "nightlife", "history"}
	BudgetBands    = []string{"1-2L", "2-3L", "3L+"}
	Paces          = []string{"relaxed", "balanced", "packed"}
	GroupTypes     = []string{"solo", "couple", "friends", "family_kids", "family_elders"}
	Diets          = []string{"veg", "jain", "eggetarian", "halal", "non_veg"}
	AvoidItems     = []string{"beef", "pork", "seafood", "egg", "onion_garlic", "alcohol"}
	SpiceLevels    = []string{"mild", "medium", "indian_spicy"}
	JapaneseLevels = []string{"none", "basic"}
)

// UserProfile holds a traveller's answers to the onboarding questionnaire. The
// fields mirror the profile columns in the users table.
type UserProfile struct {
	Interests     []string `json:"interests"`
	BudgetBand    string   `json:"budget_band"`
	Pace          string   `json:"pace"`
	GroupType     string   `json:"group_type"`
	Diet          string   `json:"diet"`
	Avoid         []string `json:"avoid"`
	SpiceLevel    string   `json:"spice_level"`
	JapaneseLevel string   `json:"japanese_level"`
	DrinksAlcohol *bool    `json:"drinks_alcohol,omitempty"`
	Notes         string   `json:"notes"`
}

func ValidateUserProfile(v *validator.Validator, profile *UserProfile) {
	v.Check(len(profile.Interests) >= 1, "interests", "must contain at least 1 interest")
	v.Check(validator.Unique(profile.Interests), "interests", "must not contain duplicate values")
	for _, interest := range profile.Interests {
		v.Check(validator.PermittedValue(interest, Interests...), "interests", mustBeOneOf(Interests))
	}

	v.Check(validator.PermittedValue(profile.BudgetBand, BudgetBands...), "budget_band", mustBeOneOf(BudgetBands))
	v.Check(validator.PermittedValue(profile.Pace, Paces...), "pace", mustBeOneOf(Paces))
	v.Check(validator.PermittedValue(profile.GroupType, GroupTypes...), "group_type", mustBeOneOf(GroupTypes))
	v.Check(validator.PermittedValue(profile.Diet, Diets...), "diet", mustBeOneOf(Diets))

	// The avoid list may be empty (plenty of non-vegetarians avoid nothing).
	v.Check(validator.Unique(profile.Avoid), "avoid", "must not contain duplicate values")
	for _, item := range profile.Avoid {
		v.Check(validator.PermittedValue(item, AvoidItems...), "avoid", mustBeOneOf(AvoidItems))
	}

	v.Check(validator.PermittedValue(profile.SpiceLevel, SpiceLevels...), "spice_level", mustBeOneOf(SpiceLevels))
	v.Check(validator.PermittedValue(profile.JapaneseLevel, JapaneseLevels...), "japanese_level", mustBeOneOf(JapaneseLevels))

	v.Check(len(profile.Notes) <= 1000, "notes", "must not be more than 1000 bytes long")
}

// mustBeOneOf builds a validation message that lists the permitted values, so
// that whoever is building the app can see straight away what is allowed.
func mustBeOneOf(permitted []string) string {
	return fmt.Sprintf("must be one of: %s", strings.Join(permitted, ", "))
}
