package planner

import (
	"slices"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// dayTripBases maps common day-trip towns to the cities they're usually
// visited from, in order of preference. A suggestion in one of these towns is
// put on a day of the first base city that's in the trip.
var dayTripBases = map[string][]string{
	"nara":     {"Kyoto", "Osaka"},
	"uji":      {"Kyoto", "Osaka"},
	"himeji":   {"Osaka", "Kyoto"},
	"kobe":     {"Osaka", "Kyoto"},
	"hakone":   {"Tokyo"},
	"nikko":    {"Tokyo"},
	"kamakura": {"Tokyo"},
	"yokohama": {"Tokyo"},
	"otaru":    {"Sapporo"},
	"niseko":   {"Sapporo"},
	"jozankei": {"Sapporo"},
}

// Arrange turns the cities and the user's chosen suggestions into days of
// stops. It's deterministic (no LLM call), so building an itinerary is
// instant and always gives the same result for the same choices:
//
//   - Each city gets its planned number of days, in route order.
//   - Places and activities are spread evenly over the city's days, and a
//     day trip (e.g. Nara) starts a new day when there's a day free, so it
//     isn't split up. The traveller's pace is already reflected in how many
//     suggestions the planner offered, so it doesn't change the spreading.
//   - Food goes in the lunch and then dinner slots, shared out across the
//     city's days.
//
// newID generates stop ids; tests pass a predictable one.
func Arrange(cities []data.CityPlan, suggestions []data.Suggestion, newID func() string) []data.Day {
	days := []data.Day{}

	for _, city := range cities {
		var sights, food []data.Suggestion
		for _, s := range suggestions {
			if baseCity(s.City, cities) != city.Name {
				continue
			}
			if s.Kind == "food" {
				food = append(food, s)
			} else {
				sights = append(sights, s)
			}
		}

		// Put the city's own sights first and group day-trip sights by town,
		// so each day trip lands on its own day. SortStableFunc keeps the
		// planner's order within each group.
		slices.SortStableFunc(sights, func(a, b data.Suggestion) int {
			aTrip, bTrip := !strings.EqualFold(a.City, city.Name), !strings.EqualFold(b.City, city.Name)
			switch {
			case aTrip != bTrip:
				if aTrip {
					return 1
				}
				return -1
			case aTrip:
				return strings.Compare(strings.ToLower(a.City), strings.ToLower(b.City))
			default:
				return 0
			}
		})

		// Spread the sights as evenly as possible: every day gets n/days
		// sights, and the first n%days days get one extra. (Rounding n/days
		// up instead would leave the last days empty.)
		capacity := func(day int) int {
			n := len(sights) / city.Days
			if day < len(sights)%city.Days {
				n++
			}
			return n
		}

		sightBins := make([][]data.Suggestion, city.Days)
		d := 0
		for _, s := range sights {
			bin := sightBins[d]
			full := len(bin) >= capacity(d)
			newTown := len(bin) > 0 && !strings.EqualFold(bin[len(bin)-1].City, s.City)
			if d < city.Days-1 && (full || newTown) {
				d++
			}
			sightBins[d] = append(sightBins[d], s)
		}

		foodBins := make([][]data.Suggestion, city.Days)
		for i, f := range food {
			foodBins[i%city.Days] = append(foodBins[i%city.Days], f)
		}

		for i := range city.Days {
			days = append(days, data.Day{
				Index: len(days) + 1,
				City:  city.Name,
				Stops: dayStops(sightBins[i], foodBins[i], newID),
			})
		}
	}

	return days
}

// eveningPlaceTypes are the kinds of place that are good to visit after dark:
// lantern-lit streets like Gion or Dotonbori, an onsen soak, or checking in to
// a ryokan. Temples, shrines, gardens and most attractions close around dusk.
var eveningPlaceTypes = []string{"street", "onsen", "ryokan"}

// dayStops orders one day's stops: a morning sight, lunch, afternoon sights,
// evening sights, then dinner. Sights that suit the evening go there; the
// rest fill the day.
func dayStops(sights, food []data.Suggestion, newID func() string) []data.Stop {
	var daytime, evening []data.Suggestion
	for _, s := range sights {
		if slices.Contains(eveningPlaceTypes, s.PlaceType) {
			evening = append(evening, s)
		} else {
			daytime = append(daytime, s)
		}
	}

	// If every sight suits the evening, still start the day with one of them.
	if len(daytime) == 0 && len(evening) > 1 {
		daytime, evening = evening[:1], evening[1:]
	}

	stops := []data.Stop{}

	add := func(s data.Suggestion, slot string) {
		stops = append(stops, data.Stop{
			ID:        newID(),
			TimeSlot:  slot,
			PlaceName: s.Name,
			PlaceType: s.PlaceType,
			City:      s.City,
			Note:      s.Reason,
			TipIDs:    []string{},
		})
	}

	if len(food) >= 3 {
		add(food[0], "breakfast")
		if len(daytime) > 0 {
			add(daytime[0], "morning")
		}
		add(food[1], "lunch")
		for _, s := range daytime[min(1, len(daytime)):] {
			add(s, "afternoon")
		}
		for _, s := range evening {
			add(s, "evening")
		}
		for _, f := range food[2:] {
			add(f, "dinner")
		}
	} else {
		if len(daytime) > 0 {
			add(daytime[0], "morning")
		}
		if len(food) > 0 {
			add(food[0], "lunch")
		}
		for _, s := range daytime[min(1, len(daytime)):] {
			add(s, "afternoon")
		}
		for _, s := range evening {
			add(s, "evening")
		}
		for _, f := range food[min(1, len(food)):] {
			add(f, "dinner")
		}
	}

	return stops
}

// baseCity returns the trip city a suggestion belongs to: the city itself if
// it's on the route, the preferred base for a known day-trip town, or else the
// city with the most days (the best guess for somewhere we don't recognise).
func baseCity(name string, cities []data.CityPlan) string {
	for _, c := range cities {
		if strings.EqualFold(c.Name, name) {
			return c.Name
		}
	}

	for _, base := range dayTripBases[strings.ToLower(name)] {
		for _, c := range cities {
			if strings.EqualFold(c.Name, base) {
				return c.Name
			}
		}
	}

	longest := cities[0]
	for _, c := range cities[1:] {
		if c.Days > longest.Days {
			longest = c
		}
	}
	return longest.Name
}
