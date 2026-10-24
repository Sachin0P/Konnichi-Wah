package desimatch

import (
	"slices"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// Match is how well a restaurant suits one traveller: a score from 1 (avoid)
// to 5 (great fit), the reasons for it, and a caution to show with it (e.g.
// "Ask for no onion or garlic"). The reasons are what make the score
// trustworthy, so every point comes from something the traveller can check.
type Match struct {
	Score   int      `json:"score"`
	Reasons []string `json:"reasons"`
	Caution string   `json:"caution,omitempty"`
}

// The cuisines OSM uses for South Asian food. Their menus nearly always have
// vegetarian dishes and food Indian travellers know.
var southAsian = []string{"indian", "nepalese", "sri_lankan", "pakistani", "bangladeshi"}

// meatCuisines are built around meat or fish stock, so they're a poor fit
// for vegetarians unless the place says it has vegetarian options.
var meatCuisines = []string{"ramen", "yakiniku", "barbecue", "tonkatsu", "steak_house", "yakitori", "sushi", "seafood", "fish", "kebab", "burger", "chicken"}

// vegNameWords in a restaurant's name mean a vegan or vegetarian kitchen.
// OSM's diet tags are often missing or set to "yes" rather than "only" (in
// testing, "Marugoto Vegan Dining" was tagged vegan=yes), so the name is a
// useful second signal: the brief asks for scoring on cuisine, keywords and
// the profile.
var vegNameWords = []string{"vegan", "vegetarian", "ヴィーガン", "ビーガン", "ベジタリアン", "菜食"}

// porkCuisines and beefCuisines rule a place out for people avoiding them.
var porkCuisines = []string{"ramen", "tonkatsu"}
var beefCuisines = []string{"yakiniku", "steak_house", "barbecue"}
var seafoodCuisines = []string{"sushi", "seafood", "fish"}

// Score rates a restaurant for a traveller's diet and avoid list.
func Score(p *data.Place, profile data.UserProfile) Match {
	cuisines := p.Cuisines()
	has := func(list []string) bool {
		return slices.ContainsFunc(cuisines, func(c string) bool { return slices.Contains(list, c) })
	}

	meaty := has(meatCuisines)

	// A veg word in the name means a vegetarian kitchen, unless the cuisine
	// is built around meat: "Ramen Toriton (ask for vegan menu)" is a ramen
	// shop with a vegan menu, not a vegan restaurant.
	names := strings.ToLower(p.Name + " " + p.NameEn)
	vegName := slices.ContainsFunc(vegNameWords, func(w string) bool { return strings.Contains(names, w) })
	vegNameKitchen := vegName && !meaty

	vegOnly := p.Tags["diet:vegetarian"] == "only" || p.Tags["diet:vegan"] == "only" || has([]string{"vegetarian", "vegan"}) || vegNameKitchen
	veganOnly := p.Tags["diet:vegan"] == "only" || has([]string{"vegan"}) || (vegNameKitchen && strings.Contains(names, "vegan"))
	vegOptions := p.Tags["diet:vegetarian"] == "yes" || p.Tags["diet:vegan"] == "yes" || vegName
	halal := p.Tags["diet:halal"] == "yes" || p.Tags["diet:halal"] == "only"
	desi := has(southAsian)
	curry := has([]string{"curry", "japanese_curry"}) || strings.Contains(strings.ToLower(p.Tags["brand"]), "coco")
	japanese := has([]string{"japanese", "ramen", "udon", "soba", "sushi", "kaiseki", "izakaya"})

	var m Match
	add := func(reason string) { m.Reasons = append(m.Reasons, reason) }

	// What the place is, in the traveller's terms.
	switch {
	case veganOnly:
		add(reasonVeganOnly)
	case vegOnly:
		add(reasonVegetarianOnly)
	case vegOptions:
		add(reasonVegOptions)
	}
	if desi {
		add(reasonDesi)
	}
	if halal {
		add(reasonHalal)
	}
	if curry && !desi {
		add(reasonCurry)
	}

	switch profile.Diet {
	case "veg", "eggetarian":
		switch {
		case vegOnly:
			m.Score = 5
		case desi:
			m.Score = 4
		case vegOptions && !meaty:
			m.Score = 4
		case vegOptions:
			m.Score = 3
		case curry:
			m.Score = 2
			m.Caution = cautionVegCurry
		default:
			m.Score = 1
		}

	case "jain":
		// Even vegetarian places usually cook with onion and garlic, so no
		// place is a sure thing; the caution says what to ask.
		switch {
		case vegOnly || desi:
			m.Score = 4
		case vegOptions && !meaty:
			m.Score = 3
		case vegOptions:
			m.Score = 2
		default:
			m.Score = 1
		}
		m.Caution = cautionJain

	case "halal":
		switch {
		case halal:
			m.Score = 5
		case vegOnly:
			m.Score = 4
			m.Caution = cautionHalalVeg
		case desi:
			m.Score = 3
			m.Caution = cautionHalalDesi
		default:
			m.Score = 1
		}

	default: // non_veg, or no diet given
		switch {
		case desi:
			m.Score = 5
		case curry:
			m.Score = 4
		default:
			m.Score = 3
		}
	}

	// Anything they avoid that the place is built around rules it out.
	for _, item := range profile.Avoid {
		switch {
		case item == "pork" && has(porkCuisines):
			m.Score, m.Caution = 1, cautionPork
		case item == "beef" && has(beefCuisines):
			m.Score, m.Caution = 1, cautionBeef
		case item == "seafood" && has(seafoodCuisines):
			m.Score, m.Caution = 1, cautionSeafood
		}
	}

	// Japanese broths and sauces hide fish stock, even in dishes that look
	// vegetarian, so warn unless the kitchen is fully vegetarian.
	if japanese && !vegOnly && m.Caution == "" && slices.Contains([]string{"veg", "jain", "eggetarian"}, profile.Diet) {
		m.Caution = cautionDashi
	}

	if profile.SpiceLevel == "indian_spicy" && desi {
		add(reasonSpicy)
	}

	if len(m.Reasons) == 0 {
		m.Reasons = []string{}
	}
	return m
}
