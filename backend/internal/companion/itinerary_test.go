package companion

import (
	"context"
	"slices"
	"strings"
	"testing"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

func TestItineraryEditingTools(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("add_itinerary_stop", `{"day_index": 2, "place_name": "Fushimi Inari-taisha", "place_type": "shrine", "time_slot": "afternoon"}`),
		text("I added Fushimi Inari-taisha to Day 2 afternoon."),
	}}

	c := Companion{LLM: llm}
	reply, err := c.Reply(context.Background(), data.ChatRequest{
		Messages: []data.ChatMessage{{Role: "user", Text: "Please add Fushimi Inari to Day 2 afternoon"}},
		Context: data.ChatContext{
			Days: []data.ChatDay{
				{Index: 1, City: "Osaka", Stops: []data.ChatStop{{PlaceName: "Dotonbori", PlaceType: "street", TimeSlot: "evening"}}},
				{Index: 2, City: "Kyoto", Stops: []data.ChatStop{{PlaceName: "Kinkaku-ji", PlaceType: "temple", TimeSlot: "morning"}}},
			},
		},
	})

	if err != nil {
		t.Fatal(err)
	}

	if !slices.Contains(reply.ToolsUsed, "add_itinerary_stop") {
		t.Errorf("expected add_itinerary_stop in tools used, got %v", reply.ToolsUsed)
	}

	if len(reply.ItineraryActions) != 1 {
		t.Fatalf("expected 1 itinerary action, got %d", len(reply.ItineraryActions))
	}

	action := reply.ItineraryActions[0]
	if action.Type != "add" || action.DayIndex != 2 || action.PlaceName != "Fushimi Inari-taisha" || action.TimeSlot != "afternoon" {
		t.Errorf("unexpected action: %+v", action)
	}

	if !strings.Contains(llm.instructions, "Day 1 (Osaka): Dotonbori") {
		t.Errorf("instructions missing full itinerary: %s", llm.instructions)
	}
}

func TestRemoveItineraryStopTool(t *testing.T) {
	llm := &fakeLLM{turns: []turn{
		call("remove_itinerary_stop", `{"day_index": 1, "place_name": "Osaka Castle"}`),
		text("I removed Osaka Castle from Day 1."),
	}}

	c := Companion{LLM: llm}
	reply, err := c.Reply(context.Background(), data.ChatRequest{
		Messages: []data.ChatMessage{{Role: "user", Text: "Remove Osaka Castle from Day 1"}},
		Context: data.ChatContext{
			Days: []data.ChatDay{
				{Index: 1, City: "Osaka", Stops: []data.ChatStop{{PlaceName: "Osaka Castle", PlaceType: "attraction", TimeSlot: "morning"}}},
			},
		},
	})

	if err != nil {
		t.Fatal(err)
	}

	if len(reply.ItineraryActions) != 1 {
		t.Fatalf("expected 1 itinerary action, got %d", len(reply.ItineraryActions))
	}

	action := reply.ItineraryActions[0]
	if action.Type != "remove" || action.DayIndex != 1 || action.PlaceName != "Osaka Castle" {
		t.Errorf("unexpected action: %+v", action)
	}
}
