package companion

import (
	"context"
	"encoding/json"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/gemini"
)

// GeminiLLM runs the companion on Google Gemini (Interactions API).
type GeminiLLM struct {
	Client *gemini.Client
}

func (g GeminiLLM) Start(instructions string, history []data.ChatMessage, tools []Tool) Conversation {
	input := make([]json.RawMessage, 0, len(history))
	for _, m := range history {
		if m.Role == "user" {
			input = append(input, gemini.UserInput(m.Text))
		} else {
			input = append(input, gemini.ModelOutput(m.Text))
		}
	}

	decls := make([]gemini.Tool, len(tools))
	for i, t := range tools {
		decls[i] = gemini.Tool{Name: t.Name, Description: t.Description, Parameters: t.Parameters}
	}

	return &geminiConversation{client: g.Client, instructions: instructions, input: input, tools: decls}
}

// geminiConversation keeps the conversation as Gemini steps. The model's own
// steps are sent back exactly as received, because they carry its thought
// signatures.
type geminiConversation struct {
	client       *gemini.Client
	instructions string
	input        []json.RawMessage
	tools        []gemini.Tool
}

func (c *geminiConversation) Next(ctx context.Context, allowTools bool) (string, []ToolCall, error) {
	var tools []gemini.Tool
	if allowTools {
		tools = c.tools
	}

	turn, err := c.client.Chat(ctx, c.instructions, c.input, tools)
	if err != nil {
		return "", nil, err
	}

	c.input = append(c.input, turn.Steps...)

	calls := make([]ToolCall, len(turn.Calls))
	for i, fc := range turn.Calls {
		calls[i] = ToolCall{ID: fc.ID, Name: fc.Name, Arguments: fc.Arguments}
	}

	return turn.Text, calls, nil
}

func (c *geminiConversation) AddToolResult(call ToolCall, result any) {
	c.input = append(c.input, gemini.FunctionResult(gemini.FunctionCall{ID: call.ID, Name: call.Name, Arguments: call.Arguments}, result))
}
