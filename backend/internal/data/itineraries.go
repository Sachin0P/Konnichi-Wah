package data

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
)

// TimeSlots lists the permitted time slots for a stop, in the order they
// happen during a day.
var TimeSlots = []string{"breakfast", "morning", "lunch", "afternoon", "evening", "dinner"}

// Itinerary is a saved trip: an ordered list of days, each with ordered stops.
type Itinerary struct {
	ID        int64     `json:"id"`
	CreatedAt time.Time `json:"created_at"`
	Title     string    `json:"title"`
	StartDate *string   `json:"start_date"` // "YYYY-MM-DD", or null if not chosen yet
	Days      []Day     `json:"days"`
	Version   int32     `json:"version"`
}

// Day is one day of the trip. Index is 1-based and always matches the day's
// position in the list; the handlers renumber it rather than trusting the
// client.
type Day struct {
	Index int    `json:"index"`
	City  string `json:"city"`
	Stops []Stop `json:"stops"`
}

// Stop is one place on a day, in visiting order. City is the stop's own city,
// which differs from the day's city on a day trip (e.g. Nara from Kyoto).
// Lat, Lng and GooglePlaceID stay null until we look places up in Google
// Places. TipIDs are etiquette rule ids, always set by the server from the
// stop's place type.
type Stop struct {
	ID            string   `json:"id"`
	TimeSlot      string   `json:"time_slot"`
	PlaceName     string   `json:"place_name"`
	PlaceType     string   `json:"place_type"`
	City          string   `json:"city"`
	Note          string   `json:"note"`
	Lat           *float64 `json:"lat"`
	Lng           *float64 `json:"lng"`
	GooglePlaceID *string  `json:"google_place_id"`
	TipIDs        []string `json:"tip_ids"`
}

func ValidateItinerary(v *validator.Validator, itinerary *Itinerary) {
	v.Check(itinerary.Title != "", "title", "must be provided")
	v.Check(len(itinerary.Title) <= 100, "title", "must not be more than 100 bytes long")

	if itinerary.StartDate != nil {
		_, err := time.Parse(time.DateOnly, *itinerary.StartDate)
		v.Check(err == nil, "start_date", "must be a date in the format YYYY-MM-DD")
	}

	v.Check(len(itinerary.Days) >= 1, "days", "must contain at least 1 day")
	v.Check(len(itinerary.Days) <= 30, "days", "must not contain more than 30 days")

	var stopIDs []string

	for i, day := range itinerary.Days {
		key := fmt.Sprintf("days[%d]", i)

		v.Check(day.City != "", key+".city", "must be provided")
		v.Check(len(day.City) <= 50, key+".city", "must not be more than 50 bytes long")
		v.Check(len(day.Stops) <= 20, key+".stops", "must not contain more than 20 stops")

		for j, stop := range day.Stops {
			key := fmt.Sprintf("days[%d].stops[%d]", i, j)

			v.Check(stop.ID != "", key+".id", "must be provided")
			v.Check(len(stop.ID) <= 64, key+".id", "must not be more than 64 bytes long")
			v.Check(validator.PermittedValue(stop.TimeSlot, TimeSlots...), key+".time_slot", mustBeOneOf(TimeSlots))
			v.Check(stop.PlaceName != "", key+".place_name", "must be provided")
			v.Check(len(stop.PlaceName) <= 100, key+".place_name", "must not be more than 100 bytes long")
			v.Check(validator.PermittedValue(stop.PlaceType, PlaceTypes...), key+".place_type", mustBeOneOf(PlaceTypes))
			v.Check(stop.City != "", key+".city", "must be provided")
			v.Check(len(stop.City) <= 50, key+".city", "must not be more than 50 bytes long")
			v.Check(len(stop.Note) <= 300, key+".note", "must not be more than 300 bytes long")

			stopIDs = append(stopIDs, stop.ID)
		}
	}

	// Stop ids must be unique across the whole itinerary, not just one day,
	// because the app uses them to track stops as they move between days.
	v.Check(validator.Unique(stopIDs), "days", "must not contain duplicate stop ids")
}

// Define an ItineraryModel struct type which wraps a sql.DB connection pool.
type ItineraryModel struct {
	DB *sql.DB
}

// Insert adds a new itinerary, and fills in the ID, CreatedAt and Version
// fields that the database generates.
func (m ItineraryModel) Insert(itinerary *Itinerary) error {
	days, err := json.Marshal(itinerary.Days)
	if err != nil {
		return err
	}

	query := `
		INSERT INTO itineraries (title, start_date, days)
		VALUES ($1, $2, $3)
		RETURNING id, created_at, version`

	args := []any{itinerary.Title, itinerary.StartDate, days}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	return m.DB.QueryRowContext(ctx, query, args...).Scan(&itinerary.ID, &itinerary.CreatedAt, &itinerary.Version)
}

func (m ItineraryModel) Get(id int64) (*Itinerary, error) {
	// The PostgreSQL bigserial type starts at 1 by default, so no itinerary
	// has an id below that. Skip the database call for those.
	if id < 1 {
		return nil, ErrRecordNotFound
	}

	// to_char() returns the date as "YYYY-MM-DD" text, or NULL if there is no
	// start date, which maps neatly onto sql.NullString.
	query := `
		SELECT id, created_at, title, to_char(start_date, 'YYYY-MM-DD'), days, version
		FROM itineraries
		WHERE id = $1`

	var itinerary Itinerary
	var startDate sql.NullString
	var days []byte

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	err := m.DB.QueryRowContext(ctx, query, id).Scan(
		&itinerary.ID,
		&itinerary.CreatedAt,
		&itinerary.Title,
		&startDate,
		&days,
		&itinerary.Version,
	)
	if err != nil {
		switch {
		case errors.Is(err, sql.ErrNoRows):
			return nil, ErrRecordNotFound
		default:
			return nil, err
		}
	}

	if startDate.Valid {
		itinerary.StartDate = &startDate.String
	}

	err = json.Unmarshal(days, &itinerary.Days)
	if err != nil {
		return nil, err
	}

	return &itinerary, nil
}

// Update saves changes to an itinerary using optimistic locking: the UPDATE
// only succeeds if the version is still the one we read. If another request
// updated the itinerary in between, no row matches and we return
// ErrEditConflict instead of silently overwriting its changes.
func (m ItineraryModel) Update(itinerary *Itinerary) error {
	days, err := json.Marshal(itinerary.Days)
	if err != nil {
		return err
	}

	query := `
		UPDATE itineraries
		SET title = $1, start_date = $2, days = $3, version = version + 1
		WHERE id = $4 AND version = $5
		RETURNING version`

	args := []any{itinerary.Title, itinerary.StartDate, days, itinerary.ID, itinerary.Version}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	err = m.DB.QueryRowContext(ctx, query, args...).Scan(&itinerary.Version)
	if err != nil {
		switch {
		case errors.Is(err, sql.ErrNoRows):
			return ErrEditConflict
		default:
			return err
		}
	}

	return nil
}
