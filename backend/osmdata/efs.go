package osmdata

import (
	"embed"
)

// places.json (restaurants and shops), help.json (hospitals, pharmacies,
// police boxes, ATMs, toilets, convenience stores) and stations.json
// (stations' English names) are snapshots of OpenStreetMap, written by cmd/osmfetch and loaded into PostgreSQL by
// cmd/seed. We keep our own copy because the public Overpass servers are
// often slow or down: in testing, one query took 19 seconds and most servers
// didn't answer.
//
// Map data (c) OpenStreetMap contributors, available under the Open Database
// Licence (https://www.openstreetmap.org/copyright).
//
//go:embed *.json
var Files embed.FS
