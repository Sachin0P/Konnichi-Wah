-- Restaurants and shops from OpenStreetMap (osmdata/places.json, loaded by
-- `make db/seed`). id is the OSM element, e.g. "node/123". The few tags we use
-- (cuisine, diet:vegetarian, shop...) are kept as JSONB, since different kinds
-- of place have different ones.
--
-- Map data (c) OpenStreetMap contributors, ODbL.
CREATE TABLE IF NOT EXISTS places (
    id text PRIMARY KEY,
    city text NOT NULL,
    name text NOT NULL,
    name_en text NOT NULL DEFAULT '',
    lat double precision NOT NULL,
    lng double precision NOT NULL,
    tags jsonb NOT NULL DEFAULT '{}'
);

-- Searches look for places in a small box around a point, so index latitude
-- (the box's first filter) together with longitude.
CREATE INDEX IF NOT EXISTS places_lat_lng_idx ON places (lat, lng);
