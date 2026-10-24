package companion

import (
	"context"
	"encoding/json"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/gemini"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"
)

// turn is one scripted model response: an answer, tool calls, or an error.
type turn struct {
	text  string
	calls []ToolCall
	err   error
}

// fakeLLM replays scripted turns and records what the companion did.
type fakeLLM struct {
	turns      []turn
	calls      int
	allowTools []bool
	results    []string // JSON of each tool result, in order
}

func (f *fakeLLM) Start(instructions string, history []data.ChatMessage, tools []Tool) Conversation {
	return f
}

func (f *fakeLLM) Next(ctx context.Context, allowTools bool) (string, []ToolCall, error) {
	t := f.turns[min(f.calls, len(f.turns)-1)]
	f.calls++
	f.allowTools = append(f.allowTools, allowTools)
	return t.text, t.calls, t.err
}

func (f *fakeLLM) AddToolResult(call ToolCall, result any) {
	js, _ := json.Marshal(result)
	f.results = append(f.results, string(js))
}

func (f *fakeLLM) lastResult() string { return f.results[len(f.results)-1] }

type fakeRules []*data.EtiquetteRule

func (f fakeRules) GetAll(placeType string) ([]*data.EtiquetteRule, error) { return f, nil }

type fakeTransit struct {
	near        []*transit.LatLng
	times       []time.Time
	night       bool   // if true, no routes before 05:00 Japan time
	unreachable string // a place name the router can't reach
}

// Places are put at made-up coordinates: Lat 1 for the unreachable place, 0
// for everything else.
func (f *fakeTransit) Geocode(ctx context.Context, name string, near *transit.LatLng) (*transit.Place, error) {
	f.near = append(f.near, near)
	if name == "Atlantis" {
		return nil, transit.ErrNotFound
	}
	loc := transit.LatLng{}
	if name == f.unreachable {
		loc.Lat = 1
	}
	return &transit.Place{Name: name, Location: loc}, nil
}

func (f *fakeTransit) Route(ctx context.Context, from, to transit.LatLng, departAt time.Time) ([]transit.Option, error) {
	f.times = append(f.times, departAt)
	if (f.night && departAt.In(japan).Hour() < 5) || to.Lat == 1 {
		return []transit.Option{}, nil
	}
	return []transit.Option{{Minutes: 30, Legs: []transit.Leg{{Mode: "subway", Line: "G Ginza Line"}}}}, nil
}

// fakePlaces returns fixed restaurants and shops around any point.
type fakePlaces struct{}

func (fakePlaces) Near(lat, lng float64, radiusM int, food bool) ([]*data.NearbyPlace, error) {
	food1 := &data.Place{Name: "Ramen Tonkotsu", Tags: map[string]string{"amenity": "restaurant", "cuisine": "ramen"}}
	food2 := &data.Place{Name: "Taste of Kerala", NameEn: "Taste of Kerala", Tags: map[string]string{"amenity": "restaurant", "cuisine": "indian"}}
	food3 := &data.Place{Name: "Bon", Tags: map[string]string{"amenity": "restaurant", "diet:vegan": "only", "cuisine": "japanese"}}
	shop1 := &data.Place{Name: "Mandarake", Tags: map[string]string{"shop": "collector"}}
	shop2 := &data.Place{Name: "Daiso", Tags: map[string]string{"shop": "variety_store"}}

	if food {
		return []*data.NearbyPlace{{Place: food1, DistanceM: 100}, {Place: food2, DistanceM: 300}, {Place: food3, DistanceM: 600}}, nil
	}
	return []*data.NearbyPlace{{Place: shop2, DistanceM: 50}, {Place: shop1, DistanceM: 200}}, nil
}

func TestFindFoodRanksForTheTravellersDiet(t *testing.T) {
	llm := &fakeLLM{turns: []turn{call("find_food", `{"near": "Senso-ji"}`), text("Try Bon.")}}

	c := newCompanion(t, llm, &fakeTransit{}, nil)
	c.Places = fakePlaces{}

	req := ask("Where can I eat near Senso-ji?")
	req.Context.Profile = &data.UserProfile{Diet: "veg"}

	if _, err := c.Reply(context.Background(), req); err != nil {
		t.Fatal(err)
	}

	// For a vegetarian: the vegan-only place first (5), then the Indian one
	// (4). The ramen shop (1) is left out, even though it's the closest.
	r := llm.lastResult()
	bon, kerala := strings.Index(r, "Bon"), strings.Index(r, "Taste of Kerala")
	if bon < 0 || kerala < 0 || bon > kerala || strings.Contains(r, "Ramen") {
		t.Errorf("got %s", r)
	}
}

// In Hindi, the tools hand the model our own Hindi: the rulebook, the diet
// card's meaning and the Desi Match reasons. The model doesn't translate them.
func TestHindiToolResults(t *testing.T) {
	rules := fakeRules{{ID: "temple-shoes", PlaceTypes: []string{"temple"}, Title: "Remove your shoes", Tip: "Take them off.", Severity: "important"}}

	tests := []struct {
		name string
		call turn
		want string
	}{
		{"etiquette", call("get_etiquette", `{"place_type": "temple"}`), "जूते उतारें"},
		{"diet card", call("show_diet_card", `{}`), "मैं शाकाहारी हूँ"},
		{"food", call("find_food", `{"near": "Senso-ji"}`), "सिर्फ़ वीगन रसोई"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			llm := &fakeLLM{turns: []turn{tt.call, text("ठीक है।")}}
			c := newCompanion(t, llm, &fakeTransit{}, rules)
			c.Places = fakePlaces{}

			req := ask("?")
			req.Language = "hi"
			req.Context.Profile = &data.UserProfile{Diet: "veg"}

			reply, err := c.Reply(context.Background(), req)
			if err != nil {
				t.Fatal(err)
			}

			got := llm.lastResult()
			if len(reply.Phrases) > 0 {
				got += reply.Phrases[0].English
			}
			if !strings.Contains(got, tt.want) {
				t.Errorf("got %s; want it to contain %q", got, tt.want)
			}
		})
	}
}

func TestInstructionsAskForHindi(t *testing.T) {
	now := time.Date(2026, 10, 26, 10, 5, 0, 0, japan)

	if got := buildInstructions(data.ChatContext{}, "hi", now); !strings.Contains(got, "Devanagari") {
		t.Error("Hindi instructions missing")
	}
	if got := buildInstructions(data.ChatContext{}, "en", now); strings.Contains(got, "Devanagari") {
		t.Error("English chat asked for Hindi")
	}
}

func TestFindPlacesFiltersByKind(t *testing.T) {
	llm := &fakeLLM{turns: []turn{call("find_places", `{"kind": "anime_figures", "near": "Akihabara"}`), text("Try Mandarake.")}}

	c := newCompanion(t, llm, &fakeTransit{}, nil)
	c.Places = fakePlaces{}

	if _, err := c.Reply(context.Background(), ask("Figure shops near Akihabara?")); err != nil {
		t.Fatal(err)
	}

	if r := llm.lastResult(); !strings.Contains(r, "Mandarake") || strings.Contains(r, "Daiso") {
		t.Errorf("got %s", r)
	}
}

func TestTrainRouteFallsBackToTheMainStation(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("get_train_route", `{"from": "Kyoto Station", "to": "Nara Park", "to_city": "Nara"}`),
		text("Take the train to Nara Station, then walk."),
	}}

	_, err := newCompanion(t, llm, &fakeTransit{unreachable: "Nara Park"}, nil).Reply(context.Background(), ask("Kyoto to Nara Park?"))
	if err != nil {
		t.Fatal(err)
	}

	if r := llm.lastResult(); !strings.Contains(r, "Nara Station, the main station in Nara") || !strings.Contains(r, "Ginza Line") {
		t.Errorf("got %s", r)
	}
}

func text(s string) turn { return turn{text: s} }

func call(name, args string) turn {
	return turn{calls: []ToolCall{{ID: "c1", Name: name, Arguments: json.RawMessage(args)}}}
}

func newCompanion(t *testing.T, llm *fakeLLM, tr *fakeTransit, rules fakeRules) Companion {
	t.Helper()
	cards, err := data.LoadPhraseCards(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}
	hindi, err := data.LoadHindi(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}
	return Companion{
		LLM:       llm,
		Rules:     rules,
		Transit:   tr,
		DietCards: cards,
		Hindi:     hindi,
		Now:       func() time.Time { return time.Date(2026, 10, 26, 1, 0, 0, 0, time.UTC) },
	}
}

func ask(question string) data.ChatRequest {
	return data.ChatRequest{
		Messages: []data.ChatMessage{{Role: "user", Text: question}},
		Context:  data.ChatContext{City: "Tokyo", DayIndex: 2},
	}
}

func TestReplyWithoutTools(t *testing.T) {
	llm := &fakeLLM{turns: []turn{text("  Konnichiwa!  ")}}

	reply, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hello"))
	if err != nil {
		t.Fatal(err)
	}
	if reply.Text != "Konnichiwa!" || len(reply.ToolsUsed) != 0 {
		t.Errorf("got %+v", reply)
	}
}

func TestReplyRunsTrainRouteTool(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("get_train_route", `{"from": "Asakusa Station", "to": "Shibuya Station"}`),
		text("Take the Ginza Line, about 30 minutes."),
	}}
	tr := &fakeTransit{}

	reply, err := newCompanion(t, llm, tr, nil).Reply(context.Background(), ask("How do I get to Shibuya?"))
	if err != nil {
		t.Fatal(err)
	}

	if !slices.Equal(reply.ToolsUsed, []string{"get_train_route"}) {
		t.Errorf("tools used: %v", reply.ToolsUsed)
	}
	if !strings.Contains(llm.lastResult(), "Ginza Line") {
		t.Errorf("route result not sent to model: %s", llm.lastResult())
	}

	// No city given in the arguments, so both searches use today's city.
	if len(tr.near) != 2 || tr.near[0] == nil || *tr.near[0] != *transit.CityCentre("tokyo") {
		t.Errorf("geocode was not biased to Tokyo: %v", tr.near)
	}
}

func TestReplyReportsUnknownPlaceToTheModel(t *testing.T) {
	llm := &fakeLLM{turns: []turn{call("get_train_route", `{"from": "Asakusa", "to": "Atlantis"}`), text("I couldn't find Atlantis.")}}

	if _, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("Route to Atlantis?")); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(llm.lastResult(), "could not find a place called Atlantis") {
		t.Errorf("got %s", llm.lastResult())
	}
}

func TestTrainRouteAtNightLooksUpTheMorningTrains(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("get_train_route", `{"from": "Kyoto Station", "to": "Nara Park", "to_city": "Nara"}`),
		text("No trains now; the first one leaves at 6."),
	}}
	tr := &fakeTransit{night: true}

	c := newCompanion(t, llm, tr, nil)
	c.Now = func() time.Time { return time.Date(2026, 10, 26, 2, 15, 0, 0, japan) }

	if _, err := c.Reply(context.Background(), ask("Kyoto to Nara?")); err != nil {
		t.Fatal(err)
	}

	if len(tr.times) != 2 || tr.times[1].Hour() != 6 {
		t.Fatalf("route lookups at %v; want now and then 06:00", tr.times)
	}
	if r := llm.lastResult(); !strings.Contains(r, "first trains after 6:00") || !strings.Contains(r, "Ginza Line") {
		t.Errorf("got %s", r)
	}
	// The Nara end of the trip must be searched near Nara, not Kyoto.
	if *tr.near[1] != *transit.CityCentre("nara") {
		t.Errorf("destination searched near %v", tr.near[1])
	}
}

func TestEtiquetteToolFiltersCityRules(t *testing.T) {
	rules := fakeRules{
		{ID: "park-nara-deer", Cities: []string{"Nara"}, Title: "Feed the deer only deer crackers", Severity: "important"},
		{ID: "park-trails", Title: "Stay on the marked paths", Severity: "helpful"},
	}
	llm := &fakeLLM{turns: []turn{call("get_etiquette", `{"place_type": "park"}`), text("Stay on the paths.")}}

	if _, err := newCompanion(t, llm, &fakeTransit{}, rules).Reply(context.Background(), ask("Park manners?")); err != nil {
		t.Fatal(err)
	}
	if r := llm.lastResult(); strings.Contains(r, "deer") || !strings.Contains(r, "marked paths") {
		t.Errorf("Tokyo park got the wrong rules: %s", r)
	}
}

func TestDietCardsComeFromTheCheckedFile(t *testing.T) {
	tests := []struct {
		name  string
		diet  string
		avoid []string
		want  []string // English of the cards shown, by prefix
	}{
		{name: "Jain", diet: "jain", avoid: []string{"onion_garlic"}, want: []string{"I am Jain.", "Does this contain dashi"}},
		{name: "Veg", diet: "veg", want: []string{"I am vegetarian. I can't", "Does this contain dashi"}},
		{name: "Non-veg avoiding beef and pork", diet: "non_veg", avoid: []string{"beef", "pork"}, want: []string{"I can't eat beef.", "I can't eat pork"}},
		{name: "Halal avoiding seafood", diet: "halal", avoid: []string{"pork", "seafood"}, want: []string{"I am Muslim.", "I can't eat fish or seafood.", "Does this contain dashi"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// The model asks for the card with a different diet than the
			// saved profile: the saved profile must win.
			llm := &fakeLLM{turns: []turn{call("show_diet_card", `{"diet": "non_veg"}`)}}

			req := ask("How do I explain my diet?")
			req.Context.Profile = &data.UserProfile{Diet: tt.diet, Avoid: tt.avoid}

			reply, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), req)
			if err != nil {
				t.Fatal(err)
			}

			if len(reply.Phrases) != len(tt.want) {
				t.Fatalf("got %d cards: %+v", len(reply.Phrases), reply.Phrases)
			}
			for i, prefix := range tt.want {
				if !strings.HasPrefix(reply.Phrases[i].English, prefix) {
					t.Errorf("card %d = %q; want prefix %q", i, reply.Phrases[i].English, prefix)
				}
			}

			// The cards are the answer: no second model call.
			if llm.calls != 1 || reply.Text != phraseCardReply {
				t.Errorf("got %d model calls and text %q", llm.calls, reply.Text)
			}
		})
	}
}

func TestJainCardSaysJaina(t *testing.T) {
	// A cheaper model once wrote ジャイアン ("Gian", the Doraemon character)
	// instead of ジャイナ for Jain. The checked card must never do that.
	cards, err := data.LoadPhraseCards(rulebook.Files)
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range cards {
		if c.Diet == "jain" && (!strings.Contains(c.Japanese, "ジャイナ教徒") || strings.Contains(c.Japanese, "ジャイアン")) {
			t.Errorf("Jain card is wrong: %s", c.Japanese)
		}
	}
}

func TestPhraseCardAnswersInOneCall(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("show_phrase_card", `{"english": "Where is the toilet?", "japanese": "トイレはどこですか？", "romaji": "Toire wa doko desu ka?"}`),
	}}

	reply, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("How do I ask for the toilet?"))
	if err != nil {
		t.Fatal(err)
	}

	want := []data.Phrase{{English: "Where is the toilet?", Japanese: "トイレはどこですか？", Romaji: "Toire wa doko desu ka?"}}
	if !slices.Equal(reply.Phrases, want) || llm.calls != 1 || reply.Text != phraseCardReply {
		t.Errorf("got %+v after %d calls", reply, llm.calls)
	}
}

func TestPhraseCardRejectsRomajiAsJapanese(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("show_phrase_card", `{"english": "Thank you", "japanese": "arigatou", "romaji": "arigatou"}`),
		text("Say arigatou gozaimasu."),
	}}

	reply, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("How do I say thanks?"))
	if err != nil {
		t.Fatal(err)
	}
	if len(reply.Phrases) != 0 || !strings.Contains(llm.lastResult(), "Japanese script") {
		t.Errorf("got phrases %+v", reply.Phrases)
	}
}

func TestReplyRetriesOnceWhenOverloaded(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		{err: &gemini.APIError{StatusCode: 503, Message: "high demand"}},
		text("Here you go."),
	}}

	reply, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hi"))
	if err != nil {
		t.Fatal(err)
	}
	if reply.Text != "Here you go." || llm.calls != 2 {
		t.Errorf("got %q after %d calls", reply.Text, llm.calls)
	}
}

func TestRetryWaitsAsLongAsAsked(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		{err: &gemini.APIError{StatusCode: 429, Message: "Rate limit exceeded. Please retry in 2s."}},
		text("Here you go."),
	}}

	start := time.Now()
	if _, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hi")); err != nil {
		t.Fatal(err)
	}
	if waited := time.Since(start); waited < 2*time.Second {
		t.Errorf("retried after %v; want at least 2s", waited)
	}
}

func TestNoRetryWhenTodaysQuotaIsGone(t *testing.T) {
	llm := &fakeLLM{turns: []turn{{err: gemini.ErrNoModels}}}

	_, err := newCompanion(t, llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hi"))
	if !OutOfQuota(err) || llm.calls != 1 {
		t.Errorf("got %v after %d calls", err, llm.calls)
	}
}

func TestReplyStopsAToolLoop(t *testing.T) {
	// A model that only ever asks for tools: after maxToolRounds we ask once
	// more with tools disallowed, and use whatever it says.
	turns := slices.Repeat([]turn{call("get_etiquette", `{"place_type": "temple"}`)}, maxToolRounds)
	llm := &fakeLLM{turns: append(turns, text("Remove your shoes."))}

	reply, err := newCompanion(t, llm, &fakeTransit{}, fakeRules{}).Reply(context.Background(), ask("Temple tips?"))
	if err != nil {
		t.Fatal(err)
	}
	if llm.calls != maxToolRounds+1 || llm.allowTools[maxToolRounds] || reply.Text != "Remove your shoes." {
		t.Errorf("got %d calls, last allowTools=%v, text %q", llm.calls, llm.allowTools[len(llm.allowTools)-1], reply.Text)
	}
}

func TestTrimRepeat(t *testing.T) {
	tests := []struct{ in, want string }{
		{
			// The real glitch from testing: two drafts glued together.
			in:   "Carry a small bag and take any rubbish back with you. Stay on the paths.Carry a small bag and bring any rubbish back with you. Stay on the marked paths.",
			want: "Carry a small bag and take any rubbish back with you. Stay on the paths.",
		},
		{
			// Repeating a phrase mid-sentence is fine, so leave it alone.
			in:   "Take the Ginza Line to Ueno, then take the Ginza Line back if you get lost on the way.",
			want: "Take the Ginza Line to Ueno, then take the Ginza Line back if you get lost on the way.",
		},
		{in: "Remove your shoes.", want: "Remove your shoes."},
	}

	for _, tt := range tests {
		if got := trimRepeat(tt.in); got != tt.want {
			t.Errorf("trimRepeat(%q)\n got %q\nwant %q", tt.in, got, tt.want)
		}
	}
}

func TestDepartAt(t *testing.T) {
	now := time.Date(2026, 10, 26, 22, 0, 0, 0, japan)

	tests := []struct {
		in   string
		want time.Time
	}{
		{"", now},
		{"23:30", time.Date(2026, 10, 26, 23, 30, 0, 0, japan)},
		{"09:00", time.Date(2026, 10, 27, 9, 0, 0, 0, japan)}, // the morning after
		{"21:30", time.Date(2026, 10, 26, 21, 30, 0, 0, japan)},
		{"soon", now},
	}

	for _, tt := range tests {
		if got := departAt(now, tt.in); !got.Equal(tt.want) {
			t.Errorf("departAt(%q) = %v; want %v", tt.in, got, tt.want)
		}
	}
}

func TestInstructionsIncludeContext(t *testing.T) {
	ctx := data.ChatContext{
		City:     "Kyoto",
		DayIndex: 3,
		Stops:    []data.ChatStop{{PlaceName: "Fushimi Inari Taisha", PlaceType: "shrine", TimeSlot: "morning"}},
		Profile:  &data.UserProfile{Diet: "jain", Avoid: []string{"onion_garlic"}, SpiceLevel: "mild", GroupType: "family_elders", JapaneseLevel: "none"},
	}

	got := buildInstructions(ctx, "", time.Date(2026, 10, 26, 10, 5, 0, 0, japan))

	for _, want := range []string{"day 3 of their trip, in Kyoto", "morning: Fushimi Inari Taisha (shrine)", "Jain", "family_elders", "10:05"} {
		if !strings.Contains(got, want) {
			t.Errorf("instructions missing %q", want)
		}
	}
}

func TestDietStatementsNeedTheCheckedCards(t *testing.T) {
	tests := []struct {
		japanese string
		diet     bool
	}{
		{"私はジャイナ教徒です。", true},
		{"肉、魚、卵は食べられません。", true},
		{"だしは入っていますか？", true},
		{"玉ねぎ抜きでお願いします。", true},
		{"トイレはどこですか？", false},
		{"牛丼をください。", false},
		{"ピーナッツアレルギーです。", false},
	}

	for _, tt := range tests {
		if got := isDietStatement(tt.japanese); got != tt.diet {
			t.Errorf("isDietStatement(%q) = %v; want %v", tt.japanese, got, tt.diet)
		}
	}
}
