-- Some rules only make sense in one place (feeding Nara's deer, geiko and
-- maiko in Kyoto). An empty list means the rule applies everywhere.
ALTER TABLE etiquette_rules ADD COLUMN IF NOT EXISTS cities text[] NOT NULL DEFAULT '{}';
