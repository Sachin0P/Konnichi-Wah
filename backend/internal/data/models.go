package data

import (
	"database/sql"
	"errors"
)

// Define custom errors that our models return. ErrRecordNotFound is returned
// when a lookup doesn't find a matching record, and ErrEditConflict when an
// update loses a race with another update (see ItineraryModel.Update).
var (
	ErrRecordNotFound = errors.New("record not found")
	ErrEditConflict   = errors.New("edit conflict")
)

// Create a Models struct which wraps all our database models. Grouping them like
// this gives us a single, convenient field on the application struct, and we
// add a new field here each time we create a new model.
type Models struct {
	EtiquetteRules EtiquetteRuleModel
	Itineraries    ItineraryModel
	Places         PlaceModel
}

// For ease of use, we add a NewModels() method which returns a Models struct
// containing the initialized models.
func NewModels(db *sql.DB) Models {
	return Models{
		EtiquetteRules: EtiquetteRuleModel{DB: db},
		Itineraries:    ItineraryModel{DB: db},
		Places:         PlaceModel{DB: db},
	}
}
