-- The id is a human-readable slug (like "temple-shoes") rather than a serial
-- number, because the JSON rulebook is the source of truth and itinerary stops
-- refer to rules by these ids, both here and in the app's offline copy.
CREATE TABLE IF NOT EXISTS etiquette_rules (
    id text PRIMARY KEY,
    place_types text[] NOT NULL,
    title text NOT NULL,
    tip text NOT NULL,
    severity text NOT NULL,
    CONSTRAINT etiquette_rules_place_types_check CHECK (array_length(place_types, 1) >= 1),
    CONSTRAINT etiquette_rules_severity_check CHECK (severity IN ('important', 'helpful'))
);
