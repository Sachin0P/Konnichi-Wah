package main

import (
	"context"
	"errors"
	"net/http"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/companion"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// chatTimeout caps one companion reply, including every tool round. It has to
// stay under the server's 30-second WriteTimeout so we can still send an
// error response.
const chatTimeout = 25 * time.Second

// The createChatReplyHandler answers one message in the companion chat. The
// app sends the conversation so far plus today's context; we keep no state.
func (app *application) createChatReplyHandler(w http.ResponseWriter, r *http.Request) {
	var input data.ChatRequest

	err := app.readJSON(w, r, &input)
	if err != nil {
		app.badRequestResponse(w, r, err)
		return
	}

	v := validator.New()

	if data.ValidateChatRequest(v, &input); !v.Valid() {
		app.failedValidationResponse(w, r, v.Errors)
		return
	}

	// Unlike the planner, the companion has no offline fallback: without an
	// LLM there's nobody to talk to.
	if app.companion == nil {
		app.serviceUnavailableResponse(w, r, "the companion is not available because no AI service is configured")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), chatTimeout)
	defer cancel()

	reply, err := app.companion.Reply(ctx, input)
	if err != nil {
		switch {
		case companion.OutOfQuota(err):
			app.logError(r, err)
			app.serviceUnavailableResponse(w, r, "the companion has used up today's free AI quota, please try again tomorrow")
		case companion.Busy(err):
			app.logError(r, err)
			app.serviceUnavailableResponse(w, r, "the companion is busy right now, please try again in a minute")
		case errors.Is(err, context.DeadlineExceeded):
			app.logError(r, err)
			app.serviceUnavailableResponse(w, r, "the companion took too long to answer, please try again")
		default:
			app.serverErrorResponse(w, r, err)
		}
		return
	}

	err = app.writeJSON(w, http.StatusOK, envelope{"reply": reply}, nil)
	if err != nil {
		app.serverErrorResponse(w, r, err)
	}
}
