package osmdata

import (
	"embed"
)

// places.json is a snapshot of the OpenStreetMap restaurants and shops we
// use, written by cmd/osmfetch and loaded into PostgreSQL by cmd/seed. We keep
// our own copy because the public Overpass servers are often slow or down:
// in testing, one query took 19 seconds and most servers didn't answer.
//
// Map data (c) OpenStreetMap contributors, available under the Open Database
// Licence (https://www.openstreetmap.org/copyright).
//
//go:embed "places.json"
var Files embed.FS
