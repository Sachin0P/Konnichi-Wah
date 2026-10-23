package companion

import (
	"context"
	"encoding/json"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/groq"
)

// GroqLLM runs the companion on Groq (OpenAI-compatible chat completions).
// It's the default for chat: much faster than Gemini's free tier, with a far
// bigger free daily quota.
type GroqLLM struct {
	Client *groq.Client
}

func (g GroqLLM) Start(instructions string, history []data.ChatMessage, tools []Tool) Conversation {
	messages := make([]groq.Message, 0, len(history)+1)
	messages = append(messages, groq.Message{Role: "system", Content: instructions})
	for _, m := range history {
		messages = append(messages, groq.Message{Role: m.Role, Content: m.Text})
	}

	decls := make([]groq.Tool, len(tools))
	for i, t := range tools {
		decls[i] = groq.Tool{Name: t.Name, Description: t.Description, Parameters: t.Parameters}
	}

	return &groqConversation{client: g.Client, messages: messages, tools: decls}
}

type groqConversation struct {
	client   *groq.Client
	messages []groq.Message
	tools    []groq.Tool
}

func (c *groqConversation) Next(ctx context.Context, allowTools bool) (string, []ToolCall, error) {
	msg, err := c.client.Chat(ctx, c.messages, c.tools, allowTools)
	if err != nil {
		return "", nil, err
	}

	// Keep the assistant's message (including its tool calls) in the history,
	// so the tool results that follow refer to calls the model can see.
	c.messages = append(c.messages, groq.Message{Role: "assistant", Content: msg.Content, ToolCalls: msg.ToolCalls})

	calls := make([]ToolCall, len(msg.ToolCalls))
	for i, tc := range msg.ToolCalls {
		// Arguments arrive as a JSON string. If the model sends something
		// that isn't JSON, pass an empty object, and the tool reports the
		// missing arguments back to the model.
		args := json.RawMessage(tc.Function.Arguments)
		if !json.Valid(args) {
			args = json.RawMessage(`{}`)
		}
		calls[i] = ToolCall{ID: tc.ID, Name: tc.Function.Name, Arguments: args}
	}

	return msg.Content, calls, nil
}

func (c *groqConversation) AddToolResult(call ToolCall, result any) {
	js, err := json.Marshal(result)
	if err != nil {
		js = []byte(`{"error": "could not encode result"}`)
	}
	c.messages = append(c.messages, groq.Message{Role: "tool", ToolCallID: call.ID, Name: call.Name, Content: string(js)})
}
