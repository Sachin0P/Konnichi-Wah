package data

import (
	"fmt"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// ChatRoles are the speakers in a companion conversation.
var ChatRoles = []string{"user", "assistant"}

// ChatMessage is one message in the conversation. The app keeps the history
// and sends it with every request, so the backend stores nothing.
type ChatMessage struct {
	Role string `json:"role"`
	Text string `json:"text"`
}

// ChatStop is a stop on today's plan, as context for the companion.
type ChatStop struct {
	PlaceName string `json:"place_name"`
	PlaceType string `json:"place_type"`
	TimeSlot  string `json:"time_slot"`
}

// ChatDay represents one day of the full itinerary for the companion.
type ChatDay struct {
	Index int        `json:"index"`
	City  string     `json:"city"`
	Stops []ChatStop `json:"stops"`
}

// ItineraryAction describes a modification to the traveller's itinerary.
type ItineraryAction struct {
	Type      string `json:"type"`                  // "add", "remove", "move"
	DayIndex  int    `json:"day_index"`             // 1-based day index
	PlaceName string `json:"place_name"`
	PlaceType string `json:"place_type,omitempty"`
	TimeSlot  string `json:"time_slot,omitempty"`
	TargetDay int    `json:"target_day,omitempty"`
}

// ChatContext tells the companion where the traveller is in their trip, so
// "how do I get to my next stop?" makes sense. Everything in it is optional.
//
// Location is where the traveller is right now, if they let the app use it,
// so "a pharmacy near me" and "how do I get there from here?" work.
type ChatContext struct {
	City     string        `json:"city"`
	DayIndex int           `json:"day_index"`
	Stops    []ChatStop    `json:"stops"`
	Days     []ChatDay     `json:"days,omitempty"`
	Profile  *UserProfile  `json:"profile"`
	Location *ChatLocation `json:"location"`
}

type ChatLocation struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

// Language is the language the traveller reads ("en" or "hi"); it's optional
// and defaults to English.
type ChatRequest struct {
	Messages []ChatMessage `json:"messages"`
	Context  ChatContext   `json:"context"`
	Language string        `json:"language"`
}

// Phrase is a Japanese phrase card the app can show to staff.
type Phrase struct {
	English  string `json:"english"`
	Japanese string `json:"japanese"`
	Romaji   string `json:"romaji"`
}

// ChatReply is the companion's answer. Phrases are shown as cards under the
// text; ToolsUsed is for debugging and the demo ("Checked live train times").
type ChatReply struct {
	Text             string            `json:"text"`
	Phrases          []Phrase          `json:"phrases"`
	ToolsUsed        []string          `json:"tools_used"`
	ItineraryActions []ItineraryAction `json:"itinerary_actions,omitempty"`
}

func ValidateChatRequest(v *validator.Validator, req *ChatRequest) {
	v.Check(len(req.Messages) >= 1, "messages", "must contain at least 1 message")
	v.Check(len(req.Messages) <= 20, "messages", "must not contain more than 20 messages")

	for i, m := range req.Messages {
		key := fmt.Sprintf("messages[%d]", i)

		v.Check(validator.PermittedValue(m.Role, ChatRoles...), key+".role", mustBeOneOf(ChatRoles))
		v.Check(m.Text != "", key+".text", "must be provided")
		v.Check(len(m.Text) <= 2000, key+".text", "must not be more than 2000 bytes long")
	}

	if req.Language != "" {
		v.Check(validator.PermittedValue(req.Language, Languages...), "language", mustBeOneOf(Languages))
	}

	// The model replies to the last message, so it must be the traveller's.
	if len(req.Messages) > 0 {
		v.Check(req.Messages[len(req.Messages)-1].Role == "user", "messages", "must end with a user message")
	}

	c := req.Context
	v.Check(len(c.City) <= 50, "context.city", "must not be more than 50 bytes long")
	v.Check(c.DayIndex >= 0 && c.DayIndex <= 30, "context.day_index", "must be between 0 and 30")
	v.Check(len(c.Stops) <= 20, "context.stops", "must not contain more than 20 stops")

	for i, s := range c.Stops {
		key := fmt.Sprintf("context.stops[%d]", i)
		v.Check(s.PlaceName != "" && len(s.PlaceName) <= 100, key+".place_name", "must be 1 to 100 bytes long")
		v.Check(validator.PermittedValue(s.PlaceType, PlaceTypes...), key+".place_type", mustBeOneOf(PlaceTypes))
		v.Check(validator.PermittedValue(s.TimeSlot, TimeSlots...), key+".time_slot", mustBeOneOf(TimeSlots))
	}

	v.Check(len(c.Days) <= 30, "context.days", "must not contain more than 30 days")
	for i, d := range c.Days {
		key := fmt.Sprintf("context.days[%d]", i)
		v.Check(d.Index >= 1 && d.Index <= 30, key+".index", "must be between 1 and 30")
		v.Check(len(d.Stops) <= 20, key+".stops", "must not contain more than 20 stops")
		for j, s := range d.Stops {
			stopKey := fmt.Sprintf("%s.stops[%d]", key, j)
			v.Check(s.PlaceName != "" && len(s.PlaceName) <= 100, stopKey+".place_name", "must be 1 to 100 bytes long")
		}
	}

	if c.Profile != nil {
		ValidateUserProfile(v, c.Profile)
	}

	if l := c.Location; l != nil {
		if l.Lat < 24 || l.Lat > 46 || l.Lng < 122 || l.Lng > 146 {
			req.Context.Location = nil
		}
	}
}
