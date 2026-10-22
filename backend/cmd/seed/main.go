package main

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"flag"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
	"github.com/Sachin0P/Konnichi-Wah/backend/rulebook"

	_ "github.com/lib/pq"
)

// The seed command loads our hand-written etiquette rulebook into PostgreSQL.
// Run it after the migrations, and again whenever rulebook/etiquette.json
// changes: make db/seed
func main() {
	var dsn string

	flag.StringVar(&dsn, "db-dsn", "", "PostgreSQL DSN")
	flag.Parse()

	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))

	// Load and validate the rules before touching the database, so that a
	// mistake in the JSON file never wipes the existing rules.
	rules, err := loadRules(rulebook.Files)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}

	db, err := openDB(dsn)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}
	defer db.Close()

	models := data.NewModels(db)

	err = models.EtiquetteRules.ReplaceAll(rules)
	if err != nil {
		logger.Error(err.Error())
		os.Exit(1)
	}

	logger.Info("etiquette rules seeded", "count", len(rules))
}

// loadRules reads etiquette.json from fsys, then checks every rule against
// data.ValidateEtiquetteRule() and makes sure no id is used twice. Taking an
// fs.FS (rather than reading rulebook.Files directly) keeps it easy to test.
func loadRules(fsys fs.FS) ([]*data.EtiquetteRule, error) {
	js, err := fs.ReadFile(fsys, "etiquette.json")
	if err != nil {
		return nil, err
	}

	// DisallowUnknownFields() catches typos in key names, like "titel", which
	// would otherwise be silently ignored.
	dec := json.NewDecoder(bytes.NewReader(js))
	dec.DisallowUnknownFields()

	var rules []*data.EtiquetteRule

	err = dec.Decode(&rules)
	if err != nil {
		return nil, fmt.Errorf("etiquette.json: %w", err)
	}

	seen := make(map[string]bool)

	for i, rule := range rules {
		v := validator.New()

		data.ValidateEtiquetteRule(v, rule)
		if !v.Valid() {
			return nil, fmt.Errorf("etiquette.json: rule %d (%q) is invalid: %v", i, rule.ID, v.Errors)
		}

		if seen[rule.ID] {
			return nil, fmt.Errorf("etiquette.json: duplicate rule id %q", rule.ID)
		}
		seen[rule.ID] = true
	}

	return rules, nil
}

func openDB(dsn string) (*sql.DB, error) {
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	err = db.PingContext(ctx)
	if err != nil {
		db.Close()
		return nil, err
	}

	return db, nil
}
