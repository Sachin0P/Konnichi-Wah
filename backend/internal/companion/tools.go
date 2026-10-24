package companion

import (
	"context"
	"encoding/json"
	"errors"
	"slices"
	"strings"
	"time"
	"unicode"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/desimatch"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
)

var japan = time.FixedZone("JST", 9*60*60)

// toolDeclarations tells the model which tools exist and what arguments they
// take. The enums come from the same lists we validate against elsewhere.
var toolDeclarations = []Tool{
	{
		Name:        "get_etiquette",
		Description: "Get etiquette rules from the app's rulebook for a kind of place, most important first.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"place_type": map[string]any{"type": "string", "enum": data.PlaceTypes},
				"city":       map[string]any{"type": "string", "description": "City where the place is, e.g. Nara for Nara Park. Some rules only apply in certain cities."},
			},
			"required": []string{"place_type"},
		},
	},
	{
		Name:        "get_train_route",
		Description: "Find public transport routes (train, subway, Shinkansen, bus) between two places in Japan, with live timetable data.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"from":        map[string]any{"type": "string", "description": "Station or landmark name, e.g. 'Asakusa Station' or 'Fushimi Inari Taisha'."},
				"from_city":   map[string]any{"type": "string", "description": "City the starting point is in, e.g. Tokyo."},
				"to":          map[string]any{"type": "string", "description": "Station or landmark name."},
				"to_city":     map[string]any{"type": "string", "description": "City the destination is in."},
				"depart_time": map[string]any{"type": "string", "description": "Optional local departure time as HH:MM. Leave out to leave now."},
			},
			"required": []string{"from", "to"},
		},
	},
	{
		Name:        "find_food",
		Description: "Find restaurants near a place that suit the traveller's diet, ranked by Desi Match (1-5) with reasons. Uses their saved diet.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"near": map[string]any{"type": "string", "description": "Station or landmark they're near, e.g. 'Senso-ji' or 'Kyoto Station'."},
				"city": map[string]any{"type": "string", "description": "City it's in, e.g. Tokyo."},
			},
			"required": []string{"near"},
		},
	},
	{
		Name:        "find_places",
		Description: "Find shops of one kind near a place, nearest first.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"kind": map[string]any{"type": "string", "enum": shopKindNames(), "description": "anime_figures: anime, figure and collector shops; toys_games: toy and game shops; hobby_models: hobby and model kit shops; hundred_yen: 100-yen and variety stores."},
				"near": map[string]any{"type": "string", "description": "Station or landmark they're near."},
				"city": map[string]any{"type": "string", "description": "City it's in, e.g. Tokyo."},
			},
			"required": []string{"kind", "near"},
		},
	},
	{
		Name:        "show_diet_card",
		Description: "Show the traveller's checked Japanese diet cards (what they can't eat) to show restaurant staff. Uses their saved diet unless you pass one.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"diet":  map[string]any{"type": "string", "enum": data.Diets},
				"avoid": map[string]any{"type": "array", "items": map[string]any{"type": "string", "enum": data.AvoidItems}},
			},
		},
	},
	{
		Name:        "show_phrase_card",
		Description: "Show the traveller a large card with a Japanese phrase they can show to staff. You provide the translation. Not for diet: use show_diet_card.",
		Parameters: map[string]any{
			"type": "object",
			"properties": map[string]any{
				"english":  map[string]any{"type": "string", "description": "What the traveller wants to say, in English."},
				"japanese": map[string]any{"type": "string", "description": "Short, polite Japanese (desu/masu form) in Japanese script."},
				"romaji":   map[string]any{"type": "string", "description": "The same Japanese in Hepburn romaji."},
			},
			"required": []string{"english", "japanese", "romaji"},
		},
	},
}

// maxPhrases caps the cards in one reply, so a confused model can't flood it.
const maxPhrases = 3

// tools runs tool calls for one Reply. Errors are returned to the model as
// {"error": "..."} rather than failing the request, so it can explain the
// problem to the traveller ("I couldn't find that station").
type tools struct {
	companion Companion
	chat      data.ChatContext
	now       time.Time
	reply     *data.ChatReply
}

func (t tools) run(ctx context.Context, call ToolCall) any {
	var result any
	var err error

	switch call.Name {
	case "get_etiquette":
		result, err = t.etiquette(call.Arguments)
	case "get_train_route":
		result, err = t.trainRoute(ctx, call.Arguments)
	case "find_food":
		result, err = t.findFood(ctx, call.Arguments)
	case "find_places":
		result, err = t.findPlaces(ctx, call.Arguments)
	case "show_diet_card":
		result, err = t.dietCard(call.Arguments)
	case "show_phrase_card":
		result, err = t.phraseCard(call.Arguments)
	default:
		err = errors.New("unknown tool")
	}

	if err != nil {
		return map[string]string{"error": err.Error()}
	}
	return result
}

func (t tools) etiquette(args json.RawMessage) (any, error) {
	var in struct {
		PlaceType string `json:"place_type"`
		City      string `json:"city"`
	}
	if err := json.Unmarshal(args, &in); err != nil {
		return nil, errors.New("invalid arguments")
	}

	city := firstNonEmpty(in.City, t.chat.City)

	rules, err := t.companion.Rules.GetAll(in.PlaceType)
	if err != nil {
		return nil, errors.New("rulebook unavailable")
	}

	type rule struct {
		Title    string `json:"title"`
		Tip      string `json:"tip"`
		Severity string `json:"severity"`
	}
	out := []rule{}
	for _, r := range rules {
		if len(out) == 6 {
			break
		}
		if r.AppliesIn(city) {
			out = append(out, rule{r.Title, r.Tip, r.Severity})
		}
	}

	return map[string]any{"rules": out}, nil
}

func (t tools) trainRoute(ctx context.Context, args json.RawMessage) (any, error) {
	var in struct {
		From       string `json:"from"`
		FromCity   string `json:"from_city"`
		To         string `json:"to"`
		ToCity     string `json:"to_city"`
		DepartTime string `json:"depart_time"`
	}
	if err := json.Unmarshal(args, &in); err != nil || in.From == "" || in.To == "" {
		return nil, errors.New("invalid arguments: from and to are required")
	}

	fromCity := firstNonEmpty(in.FromCity, t.chat.City)
	toCity := firstNonEmpty(in.ToCity, fromCity)

	from, err := t.companion.Transit.Geocode(ctx, in.From, centre(fromCity))
	if err != nil {
		return nil, t.placeError(in.From, err)
	}
	to, err := t.companion.Transit.Geocode(ctx, in.To, centre(toCity))
	if err != nil {
		return nil, t.placeError(in.To, err)
	}

	result := map[string]any{"from": from.Name, "to": to.Name}
	var notes []string

	options, night, err := t.route(ctx, from.Location, to.Location, departAt(t.now, in.DepartTime))
	if err != nil {
		return nil, err
	}

	// Some landmarks (big parks, hilltop shrines) are too far from any stop
	// for the router to reach. Route to the city's main station instead and
	// say so, so the model can add "then walk or take a bus".
	if len(options) == 0 {
		if station, err := t.companion.Transit.Geocode(ctx, toCity+" Station", centre(toCity)); err == nil {
			options, night, err = t.route(ctx, from.Location, station.Location, departAt(t.now, in.DepartTime))
			if err != nil {
				return nil, err
			}
			if len(options) > 0 {
				result["to"] = station.Name
				notes = append(notes, "There's no direct route to "+in.To+". These go to "+station.Name+", the main station in "+toCity+"; from there walk, take a local bus or a taxi.")
			}
		}
	}

	if len(options) == 0 {
		return nil, errors.New("no public transport route found")
	}

	// Name both ends of every option, and say plainly where it ends, so the
	// model doesn't present the last walk out of a station as a walk to the
	// sight the traveller asked about.
	end, _ := result["to"].(string)
	for i := range options {
		options[i].Label(from.Name, end)
	}
	result["route_ends_at"] = end

	if night {
		notes = append(notes, "No trains run at this time of night. These options are for the first trains after 6:00; a taxi is the only way to go now.")
	}
	if len(notes) > 0 {
		result["note"] = strings.Join(notes, " ")
	}

	result["options"] = options
	return result, nil
}

// route looks up options leaving at the given time. Trains in Japan stop
// around midnight and restart around 5am, so someone asking at 2am almost
// certainly wants the morning trains: if nothing runs now, it looks up the
// first trains after 6:00 instead and reports night as true.
func (t tools) route(ctx context.Context, from, to transit.LatLng, at time.Time) (options []transit.Option, night bool, err error) {
	options, err = t.companion.Transit.Route(ctx, from, to, at)
	if err != nil {
		t.companion.log("transit route failed", "error", err)
		return nil, false, errors.New("route service unavailable")
	}

	if len(options) == 0 && at.Hour() < 5 {
		morning := time.Date(at.Year(), at.Month(), at.Day(), 6, 0, 0, 0, japan)
		options, err = t.companion.Transit.Route(ctx, from, to, morning)
		if err != nil {
			t.companion.log("transit route failed", "error", err)
			return nil, false, errors.New("route service unavailable")
		}
		return options, len(options) > 0, nil
	}

	return options, false, nil
}

// searchRadiusM is how far from the named place find_places looks: about a
// 10 to 15 minute walk. (find_food widens its search as needed; see
// desimatch.Search.)
const searchRadiusM = 1000

// locate turns a place name into a point for a search, aiming at the city.
func (t tools) locate(ctx context.Context, near, city string) (*transit.Place, error) {
	near = strings.TrimSpace(near)
	if near == "" {
		return nil, errors.New("invalid arguments: near is required")
	}
	place, err := t.companion.Transit.Geocode(ctx, near, centre(firstNonEmpty(city, t.chat.City)))
	if err != nil {
		return nil, t.placeError(near, err)
	}
	return place, nil
}

func (t tools) findFood(ctx context.Context, args json.RawMessage) (any, error) {
	var in struct {
		Near string `json:"near"`
		City string `json:"city"`
	}
	if err := json.Unmarshal(args, &in); err != nil {
		return nil, errors.New("invalid arguments")
	}

	where, err := t.locate(ctx, in.Near, in.City)
	if err != nil {
		return nil, err
	}

	profile := data.UserProfile{Diet: "non_veg"}
	if t.chat.Profile != nil {
		profile = *t.chat.Profile
	}

	ranked, err := desimatch.Search(t.companion.Places, where.Location.Lat, where.Location.Lng, profile, 5)
	if err != nil {
		t.companion.log("places search failed", "error", err)
		return nil, errors.New("restaurant search unavailable")
	}

	// Send the model just what it needs, so the reply stays inside the free
	// tier's token limits.
	type restaurant struct {
		Name      string   `json:"name"`
		Cuisine   string   `json:"cuisine,omitempty"`
		DistanceM int      `json:"distance_m"`
		DesiMatch int      `json:"desi_match"`
		Reasons   []string `json:"reasons"`
		Caution   string   `json:"caution,omitempty"`
	}
	out := []restaurant{}
	for _, r := range ranked {
		out = append(out, restaurant{
			Name:      r.DisplayName(),
			Cuisine:   r.Tags["cuisine"],
			DistanceM: r.DistanceM,
			DesiMatch: r.Match.Score,
			Reasons:   r.Match.Reasons,
			Caution:   r.Match.Caution,
		})
	}

	if len(out) == 0 {
		return map[string]any{"near": where.Name, "restaurants": out, "note": "No restaurants suited to their diet are listed within 3 km. Suggest the diet card and asking staff, or a convenience store."}, nil
	}
	return map[string]any{"near": where.Name, "restaurants": out}, nil
}

// shopKinds maps find_places kinds to OpenStreetMap shop tags.
var shopKinds = map[string][]string{
	"anime_figures": {"anime", "collector"},
	"toys_games":    {"toys", "games"},
	"hobby_models":  {"hobby", "model"},
	"hundred_yen":   {"variety_store"},
}

func shopKindNames() []string {
	names := make([]string, 0, len(shopKinds))
	for k := range shopKinds {
		names = append(names, k)
	}
	slices.Sort(names)
	return names
}

func (t tools) findPlaces(ctx context.Context, args json.RawMessage) (any, error) {
	var in struct {
		Kind string `json:"kind"`
		Near string `json:"near"`
		City string `json:"city"`
	}
	if err := json.Unmarshal(args, &in); err != nil {
		return nil, errors.New("invalid arguments")
	}

	shopTags, ok := shopKinds[in.Kind]
	if !ok {
		return nil, errors.New("invalid arguments: kind must be one of " + strings.Join(shopKindNames(), ", "))
	}

	where, err := t.locate(ctx, in.Near, in.City)
	if err != nil {
		return nil, err
	}

	places, err := t.companion.Places.Near(where.Location.Lat, where.Location.Lng, searchRadiusM, false)
	if err != nil {
		t.companion.log("places search failed", "error", err)
		return nil, errors.New("shop search unavailable")
	}

	type shop struct {
		Name      string `json:"name"`
		Kind      string `json:"kind"`
		DistanceM int    `json:"distance_m"`
		Hours     string `json:"opening_hours,omitempty"`
	}
	out := []shop{}
	for _, p := range places {
		if len(out) == 8 {
			break
		}
		if slices.Contains(shopTags, p.Tags["shop"]) {
			out = append(out, shop{Name: p.DisplayName(), Kind: p.Tags["shop"], DistanceM: p.DistanceM, Hours: p.Tags["opening_hours"]})
		}
	}

	return map[string]any{"near": where.Name, "shops": out}, nil
}

// dietCovers lists the avoid items each diet's card already covers, so we
// don't add a separate "no pork" card for a vegetarian.
var dietCovers = map[string][]string{
	"veg":        {"beef", "pork", "seafood", "egg"},
	"jain":       {"beef", "pork", "seafood", "egg", "onion_garlic"},
	"eggetarian": {"beef", "pork", "seafood"},
	"halal":      {"pork"},
}

// dietCard picks the traveller's checked diet cards: the card for their diet,
// then a card for each avoid item it doesn't cover, then (for anyone who
// can't have fish stock) the "does this contain dashi?" question.
func (t tools) dietCard(args json.RawMessage) (any, error) {
	var in struct {
		Diet  string   `json:"diet"`
		Avoid []string `json:"avoid"`
	}
	_ = json.Unmarshal(args, &in) // both fields are optional

	// Prefer the saved profile: it's what the traveller told us, rather than
	// what the model inferred from the conversation.
	diet, avoid := in.Diet, in.Avoid
	if p := t.chat.Profile; p != nil {
		diet, avoid = p.Diet, p.Avoid
	}
	if diet == "" {
		return nil, errors.New("diet unknown: ask the traveller about their diet first")
	}

	var cards []data.PhraseCard
	find := func(match func(data.PhraseCard) bool) {
		for _, c := range t.companion.DietCards {
			if match(c) {
				cards = append(cards, c)
				return
			}
		}
	}

	find(func(c data.PhraseCard) bool { return c.Diet == diet })
	for _, item := range avoid {
		if !slices.Contains(dietCovers[diet], item) {
			find(func(c data.PhraseCard) bool { return c.Avoid == item })
		}
	}
	if diet == "veg" || diet == "jain" || diet == "eggetarian" || slices.Contains(avoid, "seafood") {
		find(func(c data.PhraseCard) bool { return c.Ask == "dashi" })
	}

	if len(cards) == 0 {
		return nil, errors.New("no diet card needed: they eat everything")
	}

	shown := []string{}
	for _, c := range cards {
		if len(t.reply.Phrases) == maxPhrases {
			break
		}
		t.reply.Phrases = append(t.reply.Phrases, c.Phrase())
		shown = append(shown, c.English)
	}

	return map[string]any{"status": "shown", "cards": shown}, nil
}

// phraseCard checks a phrase the model translated and adds it to the reply as
// a card. The model translates (it's good at it, and it saves a whole extra
// rate-limited call); we only check the card is usable: every part present,
// not too long, and the "Japanese" actually written in Japanese script.
// Diet statements never come here; they use the checked cards above.
func (t tools) phraseCard(args json.RawMessage) (any, error) {
	var in data.Phrase
	if err := json.Unmarshal(args, &in); err != nil {
		return nil, errors.New("invalid arguments")
	}

	in.English = strings.TrimSpace(in.English)
	in.Japanese = strings.TrimSpace(in.Japanese)
	in.Romaji = strings.TrimSpace(in.Romaji)

	switch {
	case in.English == "" || in.Japanese == "" || in.Romaji == "":
		return nil, errors.New("english, japanese and romaji are all required")
	case len(in.English) > 300 || len(in.Japanese) > 300 || len(in.Romaji) > 300:
		return nil, errors.New("phrase is too long for a card")
	case !hasJapaneseScript(in.Japanese):
		return nil, errors.New("japanese must be written in Japanese script")
	case len(t.reply.Phrases) >= maxPhrases:
		return nil, errors.New("too many phrase cards in one reply")
	}

	t.reply.Phrases = append(t.reply.Phrases, in)

	return map[string]string{"status": "shown"}, nil
}

// hasJapaneseScript reports whether s contains hiragana, katakana or kanji.
func hasJapaneseScript(s string) bool {
	for _, r := range s {
		if unicode.In(r, unicode.Hiragana, unicode.Katakana, unicode.Han) {
			return true
		}
	}
	return false
}

// departAt works out when to leave: now, or today at HH:MM in Japan time. A
// time more than an hour in the past means tomorrow ("09:00" asked at 22:00).
func departAt(now time.Time, hhmm string) time.Time {
	if hhmm == "" {
		return now
	}

	t, err := time.ParseInLocation("15:04", hhmm, japan)
	if err != nil {
		return now
	}

	at := time.Date(now.Year(), now.Month(), now.Day(), t.Hour(), t.Minute(), 0, 0, japan)
	if at.Before(now.Add(-time.Hour)) {
		at = at.AddDate(0, 0, 1)
	}
	return at
}

func centre(city string) *transit.LatLng {
	return transit.CityCentre(city)
}

// placeError turns a geocoding error into a message for the model. The model
// only needs to know whether the place exists; the real error goes to the
// log so we can debug it.
func (t tools) placeError(name string, err error) error {
	if errors.Is(err, transit.ErrNotFound) {
		return errors.New("could not find a place called " + name)
	}
	t.companion.log("transit geocode failed", "place", name, "error", err)
	return errors.New("place search unavailable")
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}
