package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func (app *application) serve() error {
	// Declare an HTTP server using the same settings as in our main() function.
	// The ErrorLog field routes any errors from Go's http.Server through our
	// structured logger at the Error level.
	//
	// The WriteTimeout is longer than the book's 10 seconds, because LLM
	// answers can take a while: up to -gemini-timeout (20s by default) for
	// the planner in the cloud, or -local-llm-timeout on a laptop. It stays
	// 5 seconds above how long handlers wait, so they can still fall back
	// or write an error.
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%d", app.config.port),
		Handler:      app.routes(),
		IdleTimeout:  time.Minute,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: max(30*time.Second, app.config.replyTimeout()+5*time.Second),
		ErrorLog:     slog.NewLogLogger(app.logger.Handler(), slog.LevelError),
	}

	// Create a shutdownError channel. We use this to receive any errors
	// returned by the graceful Shutdown() function.
	shutdownError := make(chan error)

	// Start a background goroutine which listens for SIGINT and SIGTERM.
	go func() {
		// The quit channel must be buffered, because signal.Notify() does not
		// wait for a receiver to be available when sending a signal.
		quit := make(chan os.Signal, 1)
		signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

		// Read the signal from the quit channel. This blocks until a signal is
		// received.
		s := <-quit

		app.logger.Info("shutting down server", "signal", s.String())

		// Give any in-flight requests 30 seconds to complete.
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()

		// Shutdown() returns nil if the graceful shutdown succeeded, or an
		// error (which may be because the context deadline was hit). We relay
		// this return value to the shutdownError channel.
		shutdownError <- srv.Shutdown(ctx)
	}()

	app.logger.Info("starting server", "addr", srv.Addr, "env", app.config.env)

	// Calling Shutdown() on our server causes ListenAndServe() to immediately
	// return a http.ErrServerClosed error. So if we see this error, it's
	// actually a good thing and an indication that the graceful shutdown has
	// started. We only return the error if it's NOT http.ErrServerClosed.
	err := srv.ListenAndServe()
	if !errors.Is(err, http.ErrServerClosed) {
		return err
	}

	// Otherwise, wait to receive the return value from Shutdown() on the
	// shutdownError channel. If it's an error, the graceful shutdown had a
	// problem.
	err = <-shutdownError
	if err != nil {
		return err
	}

	app.logger.Info("stopped server", "addr", srv.Addr)

	return nil
}
