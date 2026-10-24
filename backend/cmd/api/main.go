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
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/groq"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/planner"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/transit"
	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"

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
	// The Groq settings for the companion chat. If apiKey is empty, the
	// companion runs on Gemini instead (or is off without either key).
	groq struct {
		apiKey  string
		models  []string
		timeout time.Duration
	}
}

// Define an application struct to hold the dependencies for our HTTP handlers,
// helpers and middleware.
//
// The planner field is an interface, so we can switch from the built-in
// catalogue to a real LLM planner without touching the handlers. The
// companion is nil when there's no LLM configured. The transit client also
// geocodes place names for /v1/food.
type application struct {
	config    config
	logger    *slog.Logger
	transit   *transit.Client
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
	flag.Func("gemini-models", "Gemini models to use, best first, comma separated", modelList(&cfg.gemini.models))

	// Groq is the companion's default: it answers in about a second, and its
	// free tier allows around 1,000 requests a day per model against
	// Gemini's 20. gpt-oss-120b handles tool calls well.
	flag.StringVar(&cfg.groq.apiKey, "groq-api-key", "", "Groq API key for the companion chat (falls back to Gemini if empty)")
	cfg.groq.models = []string{"openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b"}
	flag.Func("groq-models", "Groq models to use, best first, comma separated", modelList(&cfg.groq.models))
	flag.DurationVar(&cfg.groq.timeout, "groq-timeout", 15*time.Second, "Groq request timeout")
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

	// The checked diet cards are embedded in the binary. A broken file is a
	// bug we want to hear about at startup, not when a traveller asks.
	dietCards, err := data.LoadPhraseCards(rulebook.Files)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}

	// The Hindi rules and card meanings are embedded too. Like the cards, a
	// broken file should stop the server starting.
	hindi, err := data.LoadHindi(rulebook.Files)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}

	// The planner uses Gemini (for its JSON schema output) with the
	// catalogue as fallback, or the catalogue alone without a key.
	var p planner.Planner = planner.Catalogue{}
	var geminiClient *gemini.Client

	if cfg.gemini.apiKey != "" {
		geminiClient = gemini.New(gemini.DefaultBaseURL, cfg.gemini.apiKey, cfg.gemini.models, cfg.gemini.timeout)
		p = planner.Fallback{
			Primary: planner.Gemini{Client: geminiClient},
			Backup:  planner.Catalogue{},
			Logger:  logger,
		}
		logger.Info("using gemini planner", "models", strings.Join(cfg.gemini.models, ","))
	} else {
		logger.Warn("no gemini api key provided, using catalogue planner only")
	}

	// The companion prefers Groq, falls back to Gemini, and is off without
	// either key (the chat endpoint then returns 503).
	var chatLLM companion.LLM
	switch {
	case cfg.groq.apiKey != "":
		chatLLM = companion.GroqLLM{Client: groq.New(groq.DefaultBaseURL, cfg.groq.apiKey, cfg.groq.models, cfg.groq.timeout)}
		logger.Info("using groq companion", "models", strings.Join(cfg.groq.models, ","))
	case geminiClient != nil:
		chatLLM = companion.GeminiLLM{Client: geminiClient}
		logger.Info("using gemini companion")
	default:
		logger.Warn("no groq or gemini api key provided, companion chat is off")
	}

	// Transitous needs no key. Name the app in the User-Agent, as its usage
	// policy asks, but never include personal data.
	transitClient := transit.New(transit.DefaultBaseURL, "KonnichiWah/"+version+" (hackathon prototype; github.com/Sachin0P/Konnichi-Wah)", 10*time.Second)

	var c *companion.Companion
	if chatLLM != nil {
		c = &companion.Companion{
			LLM:       chatLLM,
			Rules:     models.EtiquetteRules,
			Transit:   transitClient,
			Places:    models.Places,
			DietCards: dietCards,
			Hindi:     hindi,
			Now:       time.Now,
			Logger:    logger,
		}
	}

	app := &application{
		config:    cfg,
		logger:    logger,
		transit:   transitClient,
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

// modelList returns a flag.Func callback that parses a comma-separated list of
// model names into dst.
func modelList(dst *[]string) func(string) error {
	return func(val string) error {
		*dst = nil
		for _, m := range strings.Split(val, ",") {
			if m = strings.TrimSpace(m); m != "" {
				*dst = append(*dst, m)
			}
		}
		if len(*dst) == 0 {
			return errors.New("must name at least one model")
		}
		return nil
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
