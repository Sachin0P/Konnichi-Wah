package planner

import (
	"context"
	"slices"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// Catalogue is a Planner that picks from a hand-curated list of suggestions
// using simple rules. It needs no network and always returns valid output, so
// it does two jobs: it's the mock planner while the Android app is being built,
// and it will be the fallback when the LLM is down or returns bad JSON.
type Catalogue struct{}

// item is one catalogue entry. The embedded Suggestion is what the app sees;
// the other fields are only used to decide whether to include it.
type item struct {
	data.Suggestion

	// base is the itinerary city this item belongs to. Day trips (like Nara)
	// have a different City for display but are based from a main city.
	base string

	// interests decides whether a place or activity is relevant. It's ignored
	// for food, since everyone has to eat.
	interests []string

	// essential items are included whatever the traveller's interests, so
	// every plan has the classic sights for its cities.
	essential bool

	// months limits an item to certain months (e.g. the Snow Festival). An
	// empty slice means any month.
	months []int

	// contains lists the ingredients you can't avoid at a food item, using the
	// same words as data.AvoidItems plus "chicken". Fish stock (dashi) counts
	// as "seafood".
	contains []string

	// halal is true for halal-certified food places.
	halal bool
}

func (c Catalogue) Suggest(ctx context.Context, req data.TripRequest) (*data.Plan, error) {
	plan := &data.Plan{
		Cities:      allocateCities(req),
		Suggestions: []data.Suggestion{},
		Source:      "catalogue",
	}

	for _, it := range catalogue {
		if !slices.ContainsFunc(plan.Cities, func(c data.CityPlan) bool { return c.Name == it.base }) {
			continue
		}

		if len(it.months) > 0 && !slices.Contains(it.months, req.TravelMonth) {
			continue
		}

		if it.Kind == "food" {
			if !canEat(req.Profile, it) {
				continue
			}
		} else if !it.essential && !overlaps(it.interests, req.Profile.Interests) {
			continue
		}

		plan.Suggestions = append(plan.Suggestions, it.Suggestion)
	}

	return plan, nil
}

// allocateCities splits the trip days between cities. Short trips stay in
// Tokyo; longer ones add Kyoto and then Osaka. Snow lovers travelling in
// winter get Sapporo too.
func allocateCities(req data.TripRequest) []data.CityPlan {
	days := req.TripDays
	cities := []data.CityPlan{}

	winter := slices.Contains([]int{12, 1, 2}, req.TravelMonth)
	if winter && slices.Contains(req.Profile.Interests, "snow") && days >= 5 {
		sapporoDays := 2
		if days >= 8 {
			sapporoDays = 3
		}

		cities = append(cities, data.CityPlan{
			Name:   "Sapporo",
			Days:   sapporoDays,
			Reason: "Deep snow, hot springs and great winter food; a short flight from Tokyo.",
		})
		days -= sapporoDays
	}

	tokyo := data.CityPlan{Name: "Tokyo", Reason: "The easiest place to start: temples, anime, shopping and plenty of vegetarian-friendly food."}
	kyoto := data.CityPlan{Name: "Kyoto", Reason: "Japan's old capital, with the best temples, shrines and gardens."}
	osaka := data.CityPlan{Name: "Osaka", Reason: "Japan's street-food capital, 15 minutes from Kyoto by train."}

	switch {
	case days <= 3:
		tokyo.Days = days
		return append([]data.CityPlan{tokyo}, cities...)
	case days <= 6:
		kyoto.Days = days / 2
		tokyo.Days = days - kyoto.Days
		return append([]data.CityPlan{tokyo, kyoto}, cities...)
	default:
		osaka.Days = days / 4
		kyoto.Days = max(2, days*3/10)
		tokyo.Days = days - kyoto.Days - osaka.Days
		return append([]data.CityPlan{tokyo, kyoto, osaka}, cities...)
	}
}

// foodFor returns the catalogue's food suggestions for the given cities that
// suit the traveller's diet. The Gemini planner uses this so that restaurant
// suggestions always come from our hand-checked list and never from the LLM,
// which can't be trusted to know whether a dish contains dashi or pork.
func foodFor(cities []data.CityPlan, profile data.UserProfile) []data.Suggestion {
	food := []data.Suggestion{}

	for _, it := range catalogue {
		if it.Kind != "food" || !canEat(profile, it) {
			continue
		}

		// LLM city names may differ in case from ours ("tokyo" vs "Tokyo").
		if slices.ContainsFunc(cities, func(c data.CityPlan) bool { return strings.EqualFold(c.Name, it.base) }) {
			food = append(food, it.Suggestion)
		}
	}

	return food
}

// canEat reports whether a traveller can eat at a food item, based on their
// diet and avoid list. A halal diet rules out pork everywhere, and beef and
// chicken unless the place is halal-certified.
func canEat(profile data.UserProfile, it item) bool {
	excluded := slices.Clone(profile.Avoid)

	switch profile.Diet {
	case "veg":
		excluded = append(excluded, "beef", "pork", "chicken", "seafood", "egg")
	case "jain":
		excluded = append(excluded, "beef", "pork", "chicken", "seafood", "egg", "onion_garlic")
	case "eggetarian":
		excluded = append(excluded, "beef", "pork", "chicken", "seafood")
	case "halal":
		excluded = append(excluded, "pork")
		if !it.halal {
			excluded = append(excluded, "beef", "chicken")
		}
	}

	return !overlaps(it.contains, excluded)
}

// overlaps reports whether the two slices have any value in common.
func overlaps(a, b []string) bool {
	return slices.ContainsFunc(a, func(s string) bool { return slices.Contains(b, s) })
}

var catalogue = []item{
	// Tokyo.
	{
		Suggestion: data.Suggestion{ID: "tokyo-sensoji", Kind: "place", Name: "Senso-ji Temple, Asakusa", City: "Tokyo", PlaceType: "temple",
			Reason: "Tokyo's oldest temple. Walk in through Nakamise street, lined with snack and souvenir stalls."},
		base: "Tokyo", interests: []string{"temples_shrines", "history"}, essential: true,
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-meiji-jingu", Kind: "place", Name: "Meiji Jingu", City: "Tokyo", PlaceType: "shrine",
			Reason: "A peaceful forest shrine right next to busy Harajuku."},
		base: "Tokyo", interests: []string{"temples_shrines", "nature"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-shibuya", Kind: "activity", Name: "Shibuya Crossing and Shibuya Sky", City: "Tokyo", PlaceType: "attraction",
			Reason: "Cross the world's busiest crossing, then watch the city light up from the rooftop deck."},
		base: "Tokyo", interests: []string{"nightlife", "shopping"}, essential: true,
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-akihabara", Kind: "activity", Name: "Akihabara", City: "Tokyo", PlaceType: "shop",
			Reason: "Tokyo's anime, manga and electronics district, full of figure shops and arcades."},
		base: "Tokyo", interests: []string{"anime", "shopping"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-ghibli-museum", Kind: "place", Name: "Ghibli Museum, Mitaka", City: "Tokyo", PlaceType: "attraction",
			Reason: "A small, magical museum by Studio Ghibli. Tickets sell out, so book as soon as sales open."},
		base: "Tokyo", interests: []string{"anime"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-teamlab", Kind: "activity", Name: "teamLab Planets, Toyosu", City: "Tokyo", PlaceType: "attraction",
			Reason: "Walk barefoot through rooms of immersive digital art; great for families and photos."},
		base: "Tokyo", interests: []string{"anime"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-shinjuku-gyoen", Kind: "place", Name: "Shinjuku Gyoen National Garden", City: "Tokyo", PlaceType: "park",
			Reason: "A huge landscaped garden and one of Tokyo's best cherry blossom spots in spring."},
		base: "Tokyo", interests: []string{"nature"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-national-museum", Kind: "place", Name: "Tokyo National Museum, Ueno", City: "Tokyo", PlaceType: "attraction",
			Reason: "Japan's largest collection of samurai armour, swords and Buddhist art."},
		base: "Tokyo", interests: []string{"history"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-tsukiji", Kind: "activity", Name: "Tsukiji Outer Market", City: "Tokyo", PlaceType: "shop",
			Reason: "Morning food stalls with fruit, tamagoyaki and sweets. Much of it is seafood, so check before you buy."},
		base: "Tokyo", interests: []string{"food"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-shinjuku-alleys", Kind: "activity", Name: "Omoide Yokocho and Golden Gai, Shinjuku", City: "Tokyo", PlaceType: "street",
			Reason: "Tiny lantern-lit alleys of bars and grill stalls; best after dark."},
		base: "Tokyo", interests: []string{"nightlife"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-ginza", Kind: "activity", Name: "Ginza", City: "Tokyo", PlaceType: "shop",
			Reason: "Tokyo's upmarket shopping street, with department stores and big tax-free shops."},
		base: "Tokyo", interests: []string{"shopping"},
	},
	{
		Suggestion: data.Suggestion{ID: "hakone-onsen", Kind: "activity", Name: "Hakone hot springs day trip", City: "Hakone", PlaceType: "onsen",
			Reason: "Hot springs with views of Mt Fuji on a clear day, about 90 minutes from Tokyo. Many places offer private baths."},
		base: "Tokyo", interests: []string{"nature"},
	},
	{
		Suggestion: data.Suggestion{ID: "hakone-ryokan", Kind: "activity", Name: "A night in a Hakone ryokan", City: "Hakone", PlaceType: "ryokan",
			Reason: "Sleep on futons, wear a yukata and eat a multi-course dinner. Tell them your diet when you book."},
		base: "Tokyo", interests: []string{"nature", "history"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-ts-tantan", Kind: "food", Name: "T's Tantan, Tokyo Station", City: "Tokyo", PlaceType: "restaurant",
			Reason: "Fully vegan ramen inside Tokyo Station; a rare chance to try ramen without meat or fish stock."},
		base: "Tokyo", contains: []string{"onion_garlic"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-nirvanam", Kind: "food", Name: "Nirvanam, Kamiyacho", City: "Tokyo", PlaceType: "restaurant",
			Reason: "South Indian thalis and dosas with plenty of vegetarian dishes. Ask about Jain preparation when you order."},
		base: "Tokyo",
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-coco-ichibanya", Kind: "food", Name: "CoCo Ichibanya curry", City: "Tokyo", PlaceType: "restaurant",
			Reason: "Japanese curry rice where you choose the spice level. Selected branches have a vegetarian curry sauce."},
		base: "Tokyo", contains: []string{"onion_garlic"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-naritaya", Kind: "food", Name: "Naritaya halal ramen, Asakusa", City: "Tokyo", PlaceType: "restaurant",
			Reason: "Halal ramen with chicken broth, a short walk from Senso-ji."},
		base: "Tokyo", contains: []string{"chicken", "onion_garlic"}, halal: true,
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-ichiran", Kind: "food", Name: "Ichiran Ramen", City: "Tokyo", PlaceType: "restaurant",
			Reason: "Famous tonkotsu (pork-bone) ramen eaten in solo booths, with your choice of spice level."},
		base: "Tokyo", contains: []string{"pork", "onion_garlic"},
	},
	{
		Suggestion: data.Suggestion{ID: "tokyo-kura-sushi", Kind: "food", Name: "Kura Sushi", City: "Tokyo", PlaceType: "restaurant",
			Reason: "Fun, affordable conveyor-belt sushi ordered from a touch screen with an English menu."},
		base: "Tokyo", contains: []string{"seafood", "egg"},
	},

	// Kyoto.
	{
		Suggestion: data.Suggestion{ID: "kyoto-fushimi-inari", Kind: "place", Name: "Fushimi Inari Taisha", City: "Kyoto", PlaceType: "shrine",
			Reason: "Thousands of red torii gates winding up the mountain. Go early in the morning to beat the crowds."},
		base: "Kyoto", interests: []string{"temples_shrines", "nature", "history"}, essential: true,
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-kiyomizu-dera", Kind: "place", Name: "Kiyomizu-dera", City: "Kyoto", PlaceType: "temple",
			Reason: "A hillside temple with a huge wooden stage and views over Kyoto."},
		base: "Kyoto", interests: []string{"temples_shrines", "history"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-kinkaku-ji", Kind: "place", Name: "Kinkaku-ji (Golden Pavilion)", City: "Kyoto", PlaceType: "temple",
			Reason: "A gold-leaf pavilion reflected in a pond; Kyoto's most famous view."},
		base: "Kyoto", interests: []string{"temples_shrines", "history"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-arashiyama", Kind: "place", Name: "Arashiyama Bamboo Grove", City: "Kyoto", PlaceType: "park",
			Reason: "Towering bamboo paths next to Tenryu-ji temple and its garden."},
		base: "Kyoto", interests: []string{"nature", "temples_shrines"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-gion", Kind: "activity", Name: "Evening walk in Gion", City: "Kyoto", PlaceType: "street",
			Reason: "Kyoto's old geisha district of wooden teahouses and lantern-lit lanes."},
		base: "Kyoto", interests: []string{"history", "nightlife"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-nishiki-market", Kind: "activity", Name: "Nishiki Market", City: "Kyoto", PlaceType: "shop",
			Reason: "A narrow covered market of pickles, sweets and snacks, known as Kyoto's kitchen."},
		base: "Kyoto", interests: []string{"food", "shopping"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-manga-museum", Kind: "place", Name: "Kyoto International Manga Museum", City: "Kyoto", PlaceType: "attraction",
			Reason: "Thousands of manga to read, in a converted old school building."},
		base: "Kyoto", interests: []string{"anime"},
	},
	{
		Suggestion: data.Suggestion{ID: "nara-park", Kind: "place", Name: "Nara Park and Todai-ji", City: "Nara", PlaceType: "park",
			Reason: "Friendly deer and a giant bronze Buddha; an easy day trip from Kyoto."},
		base: "Kyoto", interests: []string{"temples_shrines", "nature", "history"},
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-shigetsu", Kind: "food", Name: "Shigetsu at Tenryu-ji", City: "Kyoto", PlaceType: "restaurant",
			Reason: "Shojin ryori: Buddhist temple cuisine made without meat, fish, onion or garlic, served inside the temple."},
		base: "Kyoto",
	},
	{
		Suggestion: data.Suggestion{ID: "kyoto-coco-ichibanya", Kind: "food", Name: "CoCo Ichibanya curry", City: "Kyoto", PlaceType: "restaurant",
			Reason: "Japanese curry rice where you choose the spice level. Selected branches have a vegetarian curry sauce."},
		base: "Kyoto", contains: []string{"onion_garlic"},
	},

	// Osaka.
	{
		Suggestion: data.Suggestion{ID: "osaka-dotonbori", Kind: "activity", Name: "Dotonbori", City: "Osaka", PlaceType: "street",
			Reason: "A neon canal-side street of food stalls and giant signs; Osaka at its liveliest."},
		base: "Osaka", interests: []string{"food", "nightlife", "shopping"}, essential: true,
	},
	{
		Suggestion: data.Suggestion{ID: "osaka-castle", Kind: "place", Name: "Osaka Castle", City: "Osaka", PlaceType: "attraction",
			Reason: "A landmark castle with a museum inside and a large park around it."},
		base: "Osaka", interests: []string{"history"},
	},
	{
		Suggestion: data.Suggestion{ID: "osaka-usj", Kind: "activity", Name: "Universal Studios Japan", City: "Osaka", PlaceType: "attraction",
			Reason: "Super Nintendo World and the Wizarding World of Harry Potter. Book Express Passes early for busy days."},
		base: "Osaka", interests: []string{"anime"},
	},
	{
		Suggestion: data.Suggestion{ID: "osaka-kuromon-market", Kind: "activity", Name: "Kuromon Market", City: "Osaka", PlaceType: "shop",
			Reason: "Street food and fresh fruit. Lots of it is seafood, so check what you are buying."},
		base: "Osaka", interests: []string{"food"},
	},
	{
		Suggestion: data.Suggestion{ID: "osaka-coco-ichibanya", Kind: "food", Name: "CoCo Ichibanya curry", City: "Osaka", PlaceType: "restaurant",
			Reason: "Japanese curry rice where you choose the spice level. Selected branches have a vegetarian curry sauce."},
		base: "Osaka", contains: []string{"onion_garlic"},
	},
	{
		Suggestion: data.Suggestion{ID: "osaka-okonomiyaki", Kind: "food", Name: "Okonomiyaki in Dotonbori", City: "Osaka", PlaceType: "restaurant",
			Reason: "Osaka's savoury pancake, grilled at your table. The batter usually contains dashi and egg, and pork is a common topping."},
		base: "Osaka", contains: []string{"seafood", "egg"},
	},

	// Sapporo (only chosen for snow lovers travelling in winter).
	{
		Suggestion: data.Suggestion{ID: "sapporo-snow-festival", Kind: "activity", Name: "Sapporo Snow Festival", City: "Sapporo", PlaceType: "street",
			Reason: "Giant snow and ice sculptures in Odori Park, held every February."},
		base: "Sapporo", interests: []string{"snow"}, months: []int{2},
	},
	{
		Suggestion: data.Suggestion{ID: "otaru-canal", Kind: "activity", Name: "Otaru Canal", City: "Otaru", PlaceType: "street",
			Reason: "A snowy canal town of glassworks and old warehouses, about 40 minutes from Sapporo."},
		base: "Sapporo", interests: []string{"snow", "history"},
	},
	{
		Suggestion: data.Suggestion{ID: "niseko-skiing", Kind: "activity", Name: "Skiing at Niseko", City: "Niseko", PlaceType: "park",
			Reason: "Some of the world's best powder snow, with English-speaking ski schools for beginners."},
		base: "Sapporo", interests: []string{"snow", "nature"},
	},
	{
		Suggestion: data.Suggestion{ID: "jozankei-onsen", Kind: "activity", Name: "Jozankei hot springs", City: "Jozankei", PlaceType: "onsen",
			Reason: "A hot-spring town in the mountains about an hour from Sapporo, magical in the snow."},
		base: "Sapporo", interests: []string{"snow", "nature"},
	},
	{
		Suggestion: data.Suggestion{ID: "sapporo-coco-ichibanya", Kind: "food", Name: "CoCo Ichibanya curry", City: "Sapporo", PlaceType: "restaurant",
			Reason: "Japanese curry rice where you choose the spice level. Selected branches have a vegetarian curry sauce."},
		base: "Sapporo", contains: []string{"onion_garlic"},
	},
}
