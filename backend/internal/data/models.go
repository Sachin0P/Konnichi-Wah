package data

import (
	"database/sql"
)

// Create a Models struct which wraps all our database models. Grouping them like
// this gives us a single, convenient field on the application struct, and we
// add a new field here each time we create a new model.
type Models struct {
	EtiquetteRules EtiquetteRuleModel
}

// For ease of use, we add a NewModels() method which returns a Models struct
// containing the initialized models.
func NewModels(db *sql.DB) Models {
	return Models{
		EtiquetteRules: EtiquetteRuleModel{DB: db},
	}
}
