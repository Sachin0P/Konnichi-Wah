package data

import (
	"context"
	"database/sql"
	"fmt"
	"regexp"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
	"github.com/lib/pq"
)

// PlaceTypes lists every kind of place the app understands. Each etiquette rule
// is tagged with one or more of these, and itinerary stops will use the same
// list, so that every stop can be matched to its tips. The planner's JSON
// schema will be built from this list too, so the LLM can't invent new types.
var PlaceTypes = []string{"temple", "shrine", "onsen", "ryokan", "restaurant", "train", "taxi", "shop", "street"}

// Severities lists the permitted severity values. "important" means breaking
// the rule could cause real offence or get you refused entry; "helpful" means
// it will make the visit smoother.
var Severities = []string{"important", "helpful"}

// RuleIDRX matches lowercase slugs like "temple-shoes".
var RuleIDRX = regexp.MustCompile(`^[a-z0-9]+(-[a-z0-9]+)*$`)

// EtiquetteRule is one rule from our hand-written rulebook. The struct tags
// match the keys in rulebook/etiquette.json and in our JSON responses.
type EtiquetteRule struct {
	ID         string   `json:"id"`
	PlaceTypes []string `json:"place_types"`
	Title      string   `json:"title"`
	Tip        string   `json:"tip"`
	Severity   string   `json:"severity"`
}

func ValidateEtiquetteRule(v *validator.Validator, rule *EtiquetteRule) {
	v.Check(rule.ID != "", "id", "must be provided")
	v.Check(validator.Matches(rule.ID, RuleIDRX), "id", "must be lowercase words separated by hyphens")

	v.Check(rule.PlaceTypes != nil, "place_types", "must be provided")
	v.Check(len(rule.PlaceTypes) >= 1, "place_types", "must contain at least 1 place type")
	v.Check(validator.Unique(rule.PlaceTypes), "place_types", "must not contain duplicate values")
	for _, placeType := range rule.PlaceTypes {
		v.Check(validator.PermittedValue(placeType, PlaceTypes...), "place_types", fmt.Sprintf("contains unknown place type %q", placeType))
	}

	v.Check(rule.Title != "", "title", "must be provided")
	v.Check(len(rule.Title) <= 80, "title", "must not be more than 80 bytes long")

	v.Check(rule.Tip != "", "tip", "must be provided")
	v.Check(len(rule.Tip) <= 400, "tip", "must not be more than 400 bytes long")

	v.Check(validator.PermittedValue(rule.Severity, Severities...), "severity", "must be important or helpful")
}

// Define an EtiquetteRuleModel struct type which wraps a sql.DB connection pool.
type EtiquetteRuleModel struct {
	DB *sql.DB
}

// GetAll returns the rules for a place type, with important rules first. If
// placeType is the empty string, it returns the whole rulebook.
func (m EtiquetteRuleModel) GetAll(placeType string) ([]*EtiquetteRule, error) {
	// The ($1 = '' OR ...) clause means the filter is skipped when no place
	// type is given, so we can use one query for both cases.
	//
	// Within each severity, rules that list the requested place type first
	// (i.e. rules written mainly for it, like "temple-shoes" for a temple) come
	// before rules shared from other place types. PostgreSQL arrays are
	// 1-indexed, so place_types[1] is the first element.
	query := `
		SELECT id, place_types, title, tip, severity
		FROM etiquette_rules
		WHERE ($1 = '' OR $1 = ANY(place_types))
		ORDER BY CASE severity WHEN 'important' THEN 0 ELSE 1 END, place_types[1] = $1 DESC, id`

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	rows, err := m.DB.QueryContext(ctx, query, placeType)
	if err != nil {
		return nil, err
	}

	// Importantly, defer a call to rows.Close() to ensure that the resultset is
	// closed before GetAll() returns.
	defer rows.Close()

	// Initialize an empty slice (rather than a nil one), so that a place type
	// with no rules is encoded as [] in the JSON response, not null.
	rules := []*EtiquetteRule{}

	for rows.Next() {
		var rule EtiquetteRule

		// pq.Array() adapts the PostgreSQL text[] column so that it can be
		// scanned into a []string.
		err := rows.Scan(
			&rule.ID,
			pq.Array(&rule.PlaceTypes),
			&rule.Title,
			&rule.Tip,
			&rule.Severity,
		)
		if err != nil {
			return nil, err
		}

		rules = append(rules, &rule)
	}

	// When the rows.Next() loop has finished, call rows.Err() to retrieve any
	// error that was encountered during the iteration.
	if err = rows.Err(); err != nil {
		return nil, err
	}

	return rules, nil
}

// ReplaceAll swaps the whole contents of the etiquette_rules table for the
// given rules. The JSON rulebook is the source of truth, so replacing
// everything (rather than upserting) means a rule deleted from the JSON file is
// deleted from the database too. Doing it in a transaction means API requests
// running at the same time see either the old rulebook or the new one, never a
// half-loaded mix.
func (m EtiquetteRuleModel) ReplaceAll(rules []*EtiquetteRule) error {
	// This runs one INSERT per rule, so we allow longer than our usual 3
	// seconds.
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	tx, err := m.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}

	// Defer a rollback. If the transaction is committed successfully, this
	// becomes a no-op.
	defer tx.Rollback()

	_, err = tx.ExecContext(ctx, `DELETE FROM etiquette_rules`)
	if err != nil {
		return err
	}

	query := `
		INSERT INTO etiquette_rules (id, place_types, title, tip, severity)
		VALUES ($1, $2, $3, $4, $5)`

	for _, rule := range rules {
		args := []any{rule.ID, pq.Array(rule.PlaceTypes), rule.Title, rule.Tip, rule.Severity}

		_, err = tx.ExecContext(ctx, query, args...)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}
