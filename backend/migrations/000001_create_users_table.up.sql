-- Users are authenticated by Firebase, so we don't store emails or password
-- hashes. firebase_uid is the link between a verified ID token and this row.
-- The profile columns mirror the onboarding questionnaire, so the backend can
-- use the taste profile for endpoints like /v1/food without the app resending it.
CREATE TABLE IF NOT EXISTS users (
    id bigserial PRIMARY KEY,
    created_at timestamp(0) with time zone NOT NULL DEFAULT NOW(),
    firebase_uid text UNIQUE NOT NULL,
    interests text[] NOT NULL DEFAULT '{}',
    budget_band text NOT NULL DEFAULT '',
    pace text NOT NULL DEFAULT '',
    group_type text NOT NULL DEFAULT '',
    diet text NOT NULL DEFAULT '',
    avoid text[] NOT NULL DEFAULT '{}',
    spice_level text NOT NULL DEFAULT '',
    japanese_level text NOT NULL DEFAULT '',
    notes text NOT NULL DEFAULT '',
    version integer NOT NULL DEFAULT 1
);
