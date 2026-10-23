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
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
)

var japan = time.FixedZone("JST", 9*60*60)

// cityCentres biases place searches towards the right city. Without it,
// "Todai-ji" can match a place in Brazil and "Nara Park" a hotel.
var cityCentres = map[string]transit.LatLng{
	"tokyo":     {Lat: 35.681, Lng: 139.767},
	"yokohama":  {Lat: 35.466, Lng: 139.622},
	"kamakura":  {Lat: 35.319, Lng: 139.550},
	"hakone":    {Lat: 35.233, Lng: 139.107},
	"nikko":     {Lat: 36.749, Lng: 139.598},
	"kyoto":     {Lat: 34.986, Lng: 135.759},
	"nara":      {Lat: 34.685, Lng: 135.805},
	"osaka":     {Lat: 34.702, Lng: 135.496},
	"kobe":      {Lat: 34.690, Lng: 135.196},
	"himeji":    {Lat: 34.827, Lng: 134.691},
	"hiroshima": {Lat: 34.398, Lng: 132.475},
	"kanazawa":  {Lat: 36.578, Lng: 136.648},
	"fukuoka":   {Lat: 33.590, Lng: 130.421},
	"sapporo":   {Lat: 43.068, Lng: 141.351},
	"otaru":     {Lat: 43.197, Lng: 140.994},
}

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
	if c, ok := cityCentres[strings.ToLower(strings.TrimSpace(city))]; ok {
		return &c
	}
	return nil
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
