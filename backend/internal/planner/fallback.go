package planner

import (
	"context"
	"log/slog"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
)

// Fallback is a Planner that tries Primary first and uses Backup if it fails
// for any reason: a timeout, a rate limit, the model being overloaded, or
// output that doesn't pass validation. This is how we "fall back gracefully on
// bad output": the user always gets a plan, and the response's source field
// tells us which planner produced it.
type Fallback struct {
	Primary Planner
	Backup  Planner
	Logger  *slog.Logger
}

func (f Fallback) Suggest(ctx context.Context, req data.TripRequest) (*data.Plan, error) {
	plan, err := f.Primary.Suggest(ctx, req)
	if err == nil {
		return plan, nil
	}

	// If the client has disconnected there's nobody to send a plan to, so
	// don't bother with the backup.
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}

	f.Logger.Warn("primary planner failed, using backup", "error", err)

	return f.Backup.Suggest(ctx, req)
}
