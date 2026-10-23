package main

import (
	"context"
	"database/sql"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/companion"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/gemini"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/planner"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"

	// Import the pq driver so that it can register itself with the database/sql
	// package. We alias the import to the blank identifier because we never use
	// the package directly.
	_ "github.com/lib/pq"
)

// Declare a string containing the application version number. Later we can
// generate this automatically at build time, but for now we hard-code it.
const version = "1.0.0"

// Define a config struct to hold all the configuration settings for our
// application. We read these settings from command-line flags when the
// application starts.
type config struct {
	port int
	env  string
	db   struct {
		dsn          string
		maxOpenConns int
		maxIdleConns int
		maxIdleTime  time.Duration
	}
	// The limiter settings control the per-IP rate limiter middleware. The
	// enabled field lets us switch it off entirely (handy when load testing).
	limiter struct {
		rps     float64
		burst   int
		enabled bool
	}
	cors struct {
		trustedOrigins []string
	}
	// The Gemini settings for the LLM planner. If apiKey is empty, the app
	// runs with the built-in catalogue planner only.
	gemini struct {
		apiKey  string
		models  []string
		timeout time.Duration
	}
}

// Define an application struct to hold the dependencies for our HTTP handlers,
// helpers and middleware. More fields (Google API clients) will be added as we
// build out the app.
//
// The planner field is an interface, so we can switch from the built-in
// catalogue to a real LLM planner without touching the handlers. The
// companion is nil when there's no LLM configured.
type application struct {
	config    config
	logger    *slog.Logger
	models    data.Models
	planner   planner.Planner
	companion *companion.Companion
}

func main() {
	var cfg config

	flag.IntVar(&cfg.port, "port", 4000, "API server port")
	flag.StringVar(&cfg.env, "env", "development", "Environment (development|staging|production)")

	// The DSN has no default, because it contains a password. The Makefile
	// passes it in from the KONNICHIWAH_DB_DSN environment variable instead.
	flag.StringVar(&cfg.db.dsn, "db-dsn", "", "PostgreSQL DSN")

	flag.IntVar(&cfg.db.maxOpenConns, "db-max-open-conns", 25, "PostgreSQL max open connections")
	flag.IntVar(&cfg.db.maxIdleConns, "db-max-idle-conns", 25, "PostgreSQL max idle connections")
	flag.DurationVar(&cfg.db.maxIdleTime, "db-max-idle-time", 15*time.Minute, "PostgreSQL max connection idle time")

	flag.Float64Var(&cfg.limiter.rps, "limiter-rps", 2, "Rate limiter maximum requests per second")
	flag.IntVar(&cfg.limiter.burst, "limiter-burst", 4, "Rate limiter maximum burst")
	flag.BoolVar(&cfg.limiter.enabled, "limiter-enabled", true, "Enable rate limiter")

	// Use flag.Func() to process the -cors-trusted-origins value, splitting it
	// on whitespace so that multiple origins can be given in one flag.
	flag.Func("cors-trusted-origins", "Trusted CORS origins (space separated)", func(val string) error {
		cfg.cors.trustedOrigins = strings.Fields(val)
		return nil
	})

	// Like the DSN, the API key has no default. The Makefile passes it in from
	// the GEMINI_API_KEY environment variable.
	flag.StringVar(&cfg.gemini.apiKey, "gemini-api-key", "", "Gemini API key (leave empty to use the catalogue planner only)")
	// A comma-separated list of models, best first. When one model's daily
	// free quota runs out, the client moves on to the next. We start with
	// gemini-3.6-flash rather than the newest Flash model because the newest
	// was often overloaded (503) in testing. (gemini-3.5-flash shares 3.6's
	// quota, so listing both gains nothing.)
	cfg.gemini.models = []string{"gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"}
	flag.Func("gemini-models", "Gemini models to use, best first, comma separated", func(val string) error {
		cfg.gemini.models = nil
		for _, m := range strings.Split(val, ",") {
			if m = strings.TrimSpace(m); m != "" {
				cfg.gemini.models = append(cfg.gemini.models, m)
			}
		}
		if len(cfg.gemini.models) == 0 {
			return errors.New("must name at least one model")
		}
		return nil
	})
	flag.DurationVar(&cfg.gemini.timeout, "gemini-timeout", 20*time.Second, "Gemini request timeout")

	displayVersion := flag.Bool("version", false, "Display version and exit")

	flag.Parse()

	if *displayVersion {
		fmt.Printf("Version:\t%s\n", version)
		os.Exit(0)
	}

	// Initialize a new structured logger which writes log entries to the
	// standard out stream.
	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))

	db, err := openDB(cfg)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}

	// Defer a call to db.Close() so that the connection pool is closed before
	// the main() function exits.
	defer db.Close()

	logger.Info("database connection pool established")

	// Use the LLM planner if we have an API key, with the catalogue as its
	// fallback. Without a key (e.g. a teammate working on the app), use the
	// catalogue on its own, so the API still works fully offline.
	models := data.NewModels(db)

	var p planner.Planner = planner.Catalogue{}
	var c *companion.Companion

	if cfg.gemini.apiKey != "" {
		llm := gemini.New(gemini.DefaultBaseURL, cfg.gemini.apiKey, cfg.gemini.models, cfg.gemini.timeout)

		p = planner.Fallback{
			Primary: planner.Gemini{Client: llm},
			Backup:  planner.Catalogue{},
			Logger:  logger,
		}

		// Transitous needs no key. Name the app in the User-Agent, as its
		// usage policy asks, but never include personal data.
		c = &companion.Companion{
			LLM:     llm,
			Rules:   models.EtiquetteRules,
			Transit: transit.New(transit.DefaultBaseURL, "KonnichiWah/"+version+" (hackathon prototype; github.com/Sachin0P/Konnichi-Wah)", 10*time.Second),
			Now:     time.Now,
			Logger:  logger,
		}

		logger.Info("using gemini planner and companion", "models", strings.Join(cfg.gemini.models, ","))
	} else {
		logger.Warn("no gemini api key provided, using catalogue planner only and no companion")
	}

	app := &application{
		config:    cfg,
		logger:    logger,
		models:    models,
		planner:   p,
		companion: c,
	}

	err = app.serve()
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}
}

// The openDB() function returns a sql.DB connection pool.
func openDB(cfg config) (*sql.DB, error) {
	// Use sql.Open() to create an empty connection pool, using the DSN from the
	// config struct.
	db, err := sql.Open("postgres", cfg.db.dsn)
	if err != nil {
		return nil, err
	}

	// Set the connection pool limits. Passing a value less than or equal to 0
	// for the max open connections means there is no limit.
	db.SetMaxOpenConns(cfg.db.maxOpenConns)
	db.SetMaxIdleConns(cfg.db.maxIdleConns)
	db.SetConnMaxIdleTime(cfg.db.maxIdleTime)

	// Create a context with a 5-second timeout deadline.
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Use PingContext() to establish a new connection to the database. If the
	// connection couldn't be established within the 5-second deadline, this
	// returns an error. If we get an error, we close the connection pool and
	// return the error.
	err = db.PingContext(ctx)
	if err != nil {
		db.Close()
		return nil, err
	}

	return db, nil
}
