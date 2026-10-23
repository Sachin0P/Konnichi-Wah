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
)

// fakeLLM replays scripted turns and records what it was sent.
type fakeLLM struct {
	turns     []*gemini.Turn
	errs      []error // errs[i] is returned instead of turns[i], if set
	inputs    [][]json.RawMessage
	toolCount []int
}

func (f *fakeLLM) Chat(ctx context.Context, instructions string, input []json.RawMessage, tools []gemini.Tool) (*gemini.Turn, error) {
	i := len(f.inputs)
	f.inputs = append(f.inputs, slices.Clone(input))
	f.toolCount = append(f.toolCount, len(tools))
	if i < len(f.errs) && f.errs[i] != nil {
		return nil, f.errs[i]
	}
	return f.turns[min(i, len(f.turns)-1)], nil
}

type fakeRules []*data.EtiquetteRule

func (f fakeRules) GetAll(placeType string) ([]*data.EtiquetteRule, error) { return f, nil }

type fakeTransit struct {
	near  []*transit.LatLng
	times []time.Time
	night bool // if true, no routes before 05:00 Japan time
}

func (f *fakeTransit) Geocode(ctx context.Context, name string, near *transit.LatLng) (*transit.Place, error) {
	f.near = append(f.near, near)
	if name == "Atlantis" {
		return nil, transit.ErrNotFound
	}
	return &transit.Place{Name: name, Location: transit.LatLng{Lat: 35, Lng: 139}}, nil
}

func (f *fakeTransit) Route(ctx context.Context, from, to transit.LatLng, departAt time.Time) ([]transit.Option, error) {
	f.times = append(f.times, departAt)
	if f.night && departAt.In(japan).Hour() < 5 {
		return []transit.Option{}, nil
	}
	return []transit.Option{{Minutes: 30, Legs: []transit.Leg{{Mode: "subway", Line: "G Ginza Line"}}}}, nil
}

func TestTrainRouteAtNightLooksUpTheMorningTrains(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{
		call("get_train_route", `{"from": "Kyoto Station", "to": "Nara Park", "to_city": "Nara"}`),
		text("No trains now; the first one leaves at 6."),
	}}
	tr := &fakeTransit{night: true}

	c := newCompanion(llm, tr, nil)
	c.Now = func() time.Time { return time.Date(2026, 10, 26, 2, 15, 0, 0, japan) }

	if _, err := c.Reply(context.Background(), ask("Kyoto to Nara?")); err != nil {
		t.Fatal(err)
	}

	if len(tr.times) != 2 || tr.times[1].Hour() != 6 {
		t.Fatalf("route lookups at %v; want now and then 06:00", tr.times)
	}
	if result := lastResult(t, llm.inputs[1]); !strings.Contains(result, "first trains after 6:00") || !strings.Contains(result, "Ginza Line") {
		t.Errorf("got %s", result)
	}

	// The Nara end of the trip must be searched near Nara, not Kyoto.
	if *tr.near[1] != cityCentres["nara"] {
		t.Errorf("destination searched near %v", tr.near[1])
	}
}

func text(s string) *gemini.Turn { return &gemini.Turn{Status: "completed", Text: s} }

func call(name, args string) *gemini.Turn {
	step := json.RawMessage(`{"type": "function_call", "id": "c1", "name": "` + name + `"}`)
	return &gemini.Turn{
		Status: "requires_action",
		Steps:  []json.RawMessage{step},
		Calls:  []gemini.FunctionCall{{ID: "c1", Name: name, Arguments: json.RawMessage(args)}},
	}
}

func newCompanion(llm *fakeLLM, tr *fakeTransit, rules fakeRules) Companion {
	return Companion{
		LLM:     llm,
		Rules:   rules,
		Transit: tr,
		Now:     func() time.Time { return time.Date(2026, 10, 26, 1, 0, 0, 0, time.UTC) },
	}
}

func ask(question string) data.ChatRequest {
	return data.ChatRequest{
		Messages: []data.ChatMessage{{Role: "user", Text: question}},
		Context:  data.ChatContext{City: "Tokyo", DayIndex: 2},
	}
}

// lastResult returns the JSON the model was sent back for its last tool call.
func lastResult(t *testing.T, input []json.RawMessage) string {
	t.Helper()
	var step struct {
		Type   string `json:"type"`
		Result []struct {
			Text string `json:"text"`
		} `json:"result"`
	}
	if err := json.Unmarshal(input[len(input)-1], &step); err != nil || step.Type != "function_result" {
		t.Fatalf("last input step is not a function result: %s", input[len(input)-1])
	}
	return step.Result[0].Text
}

func TestReplyWithoutTools(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{text("  Konnichiwa!  ")}}

	reply, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hello"))
	if err != nil {
		t.Fatal(err)
	}
	if reply.Text != "Konnichiwa!" || len(reply.ToolsUsed) != 0 {
		t.Errorf("got %+v", reply)
	}
}

func TestReplyRunsTrainRouteTool(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{
		call("get_train_route", `{"from": "Asakusa Station", "to": "Shibuya Station"}`),
		text("Take the Ginza Line, about 30 minutes."),
	}}
	tr := &fakeTransit{}

	reply, err := newCompanion(llm, tr, nil).Reply(context.Background(), ask("How do I get to Shibuya?"))
	if err != nil {
		t.Fatal(err)
	}

	if !slices.Equal(reply.ToolsUsed, []string{"get_train_route"}) {
		t.Errorf("tools used: %v", reply.ToolsUsed)
	}

	// The second model call must include the model's own step and our result.
	result := lastResult(t, llm.inputs[1])
	if !strings.Contains(result, "Ginza Line") {
		t.Errorf("route result not sent to model: %s", result)
	}

	// No city given in the arguments, so both searches use today's city.
	if len(tr.near) != 2 || tr.near[0] == nil || *tr.near[0] != cityCentres["tokyo"] {
		t.Errorf("geocode was not biased to Tokyo: %v", tr.near)
	}
}

func TestReplyReportsUnknownPlaceToTheModel(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{
		call("get_train_route", `{"from": "Asakusa", "to": "Atlantis"}`),
		text("I couldn't find Atlantis."),
	}}

	_, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("Route to Atlantis?"))
	if err != nil {
		t.Fatal(err)
	}

	if result := lastResult(t, llm.inputs[1]); !strings.Contains(result, "could not find a place called Atlantis") {
		t.Errorf("got %s", result)
	}
}

func TestEtiquetteToolFiltersCityRules(t *testing.T) {
	rules := fakeRules{
		{ID: "park-nara-deer", Cities: []string{"Nara"}, Title: "Feed the deer only deer crackers", Severity: "important"},
		{ID: "park-trails", Title: "Stay on the marked paths", Severity: "helpful"},
	}
	llm := &fakeLLM{turns: []*gemini.Turn{call("get_etiquette", `{"place_type": "park"}`), text("Stay on the paths.")}}

	_, err := newCompanion(llm, &fakeTransit{}, rules).Reply(context.Background(), ask("Park manners?"))
	if err != nil {
		t.Fatal(err)
	}

	result := lastResult(t, llm.inputs[1])
	if strings.Contains(result, "deer") || !strings.Contains(result, "marked paths") {
		t.Errorf("Tokyo park got the wrong rules: %s", result)
	}
}

func TestPhraseCardAnswersInOneCall(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{
		call("show_phrase_card", `{"english": "No fish stock, please", "japanese": "だしは入れないでください", "romaji": "Dashi wa irenaide kudasai"}`),
	}}

	reply, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("How do I say no fish stock?"))
	if err != nil {
		t.Fatal(err)
	}

	want := []data.Phrase{{English: "No fish stock, please", Japanese: "だしは入れないでください", Romaji: "Dashi wa irenaide kudasai"}}
	if !slices.Equal(reply.Phrases, want) {
		t.Errorf("got %+v", reply.Phrases)
	}

	// The card is the answer, so we must not spend a second model call.
	if len(llm.inputs) != 1 || reply.Text != phraseCardReply {
		t.Errorf("got %d model calls and text %q", len(llm.inputs), reply.Text)
	}
}

func TestPhraseCardRejectsRomajiAsJapanese(t *testing.T) {
	llm := &fakeLLM{turns: []*gemini.Turn{
		call("show_phrase_card", `{"english": "Thank you", "japanese": "arigatou", "romaji": "arigatou"}`),
		text("Say arigatou gozaimasu."),
	}}

	reply, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("How do I say thanks?"))
	if err != nil {
		t.Fatal(err)
	}

	// No card, and the model was told why so it could answer in text.
	if len(reply.Phrases) != 0 || !strings.Contains(lastResult(t, llm.inputs[1]), "Japanese script") {
		t.Errorf("got phrases %+v", reply.Phrases)
	}
}

func TestRetryWaitsAsLongAsGoogleAsks(t *testing.T) {
	llm := &fakeLLM{
		turns: []*gemini.Turn{nil, text("Here you go.")},
		errs:  []error{&gemini.APIError{StatusCode: 429, Message: "Rate limit exceeded. Please retry in 2s or upgrade your tier."}},
	}

	start := time.Now()
	_, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hi"))
	if err != nil {
		t.Fatal(err)
	}
	if waited := time.Since(start); waited < 2*time.Second {
		t.Errorf("retried after %v; want at least the 2s Google asked for", waited)
	}
}

func TestReplyRetriesOnceWhenOverloaded(t *testing.T) {
	llm := &fakeLLM{
		turns: []*gemini.Turn{nil, text("Here you go.")},
		errs:  []error{&gemini.APIError{StatusCode: 503, Message: "high demand"}},
	}

	reply, err := newCompanion(llm, &fakeTransit{}, nil).Reply(context.Background(), ask("hi"))
	if err != nil {
		t.Fatal(err)
	}
	if reply.Text != "Here you go." || len(llm.inputs) != 2 {
		t.Errorf("got %q after %d calls", reply.Text, len(llm.inputs))
	}
}

func TestReplyStopsAToolLoop(t *testing.T) {
	// A model that only ever asks for tools: after maxToolRounds we call it
	// once more with no tools, and use whatever it says.
	llm := &fakeLLM{turns: []*gemini.Turn{call("get_etiquette", `{"place_type": "temple"}`)}}
	llm.turns = append(slices.Repeat(llm.turns, maxToolRounds), text("Remove your shoes."))

	reply, err := newCompanion(llm, &fakeTransit{}, fakeRules{}).Reply(context.Background(), ask("Temple tips?"))
	if err != nil {
		t.Fatal(err)
	}

	if len(llm.inputs) != maxToolRounds+1 || llm.toolCount[maxToolRounds] != 0 {
		t.Errorf("got %d calls, last with %d tools", len(llm.inputs), llm.toolCount[len(llm.toolCount)-1])
	}
	if reply.Text != "Remove your shoes." {
		t.Errorf("got %q", reply.Text)
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

	got := buildInstructions(ctx, time.Date(2026, 10, 26, 10, 5, 0, 0, japan))

	for _, want := range []string{"day 3 of their trip, in Kyoto", "morning: Fushimi Inari Taisha (shrine)", "Jain", "family_elders", "10:05"} {
		if !strings.Contains(got, want) {
			t.Errorf("instructions missing %q", want)
		}
	}
}
