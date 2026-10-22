package planner

import (
	"context"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// Planner turns a trip request into suggestions. Handlers only depend on this
// interface, so we can swap the built-in Catalogue for a real LLM planner (and
// keep the Catalogue as its fallback) without changing any handler code.
type Planner interface {
	Suggest(ctx context.Context, req data.TripRequest) (*data.Plan, error)
}
