package main

import (
	"context"
	"database/sql"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/data"
	_ "github.com/lib/pq"
)

func main() {
	var dsn string
	flag.StringVar(&dsn, "db-dsn", "", "PostgreSQL DSN")
	flag.Parse()

	if dsn == "" {
		dsn = os.Getenv("KONNICHIWAH_DB_DSN")
	}

	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		logger.Error("failed to connect to db", "err", err)
		os.Exit(1)
	}
	defer db.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	rows, err := db.QueryContext(ctx, "SELECT id, name, tags->>'amenity' FROM places WHERE name_en = '' OR name_en IS NULL;")
	if err != nil {
		logger.Error("query failed", "err", err)
		os.Exit(1)
	}
	defer rows.Close()

	type placeUpdate struct {
		id     string
		nameEn string
	}
	var updates []placeUpdate

	for rows.Next() {
		var id, name string
		var amenity sql.NullString
		if err := rows.Scan(&id, &name, &amenity); err != nil {
			logger.Error("scan failed", "err", err)
			continue
		}
		kind := amenity.String
		if kind == "" {
			kind = "place"
		}
		en := data.EnglishPlaceName(name, kind)
		if en != "" && en != name {
			updates = append(updates, placeUpdate{id: id, nameEn: en})
		}
	}

	logger.Info(fmt.Sprintf("Found %d places to enrich with English names", len(updates)))

	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		logger.Error("begin tx failed", "err", err)
		os.Exit(1)
	}
	defer tx.Rollback()

	stmt, err := tx.PrepareContext(ctx, "UPDATE places SET name_en = $1 WHERE id = $2;")
	if err != nil {
		logger.Error("prepare stmt failed", "err", err)
		os.Exit(1)
	}
	defer stmt.Close()

	count := 0
	for _, u := range updates {
		if _, err := stmt.ExecContext(ctx, u.nameEn, u.id); err != nil {
			logger.Error("update failed", "id", u.id, "err", err)
			continue
		}
		count++
	}

	if err := tx.Commit(); err != nil {
		logger.Error("commit failed", "err", err)
		os.Exit(1)
	}

	logger.Info(fmt.Sprintf("Successfully enriched %d places in PostgreSQL with English names!", count))
}
