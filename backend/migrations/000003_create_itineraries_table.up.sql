-- The days and their stops are stored as one JSONB document rather than in
-- separate tables. The app edits the whole list offline (reorder, delete,
-- add) and sends it back in one PATCH, so the backend replaces it wholesale;
-- separate rows would only add diffing for no benefit. Go validates the shape
-- (data.ValidateItinerary) before anything is written.
--
-- user_id stays NULL until Firebase authentication is added.
CREATE TABLE IF NOT EXISTS itineraries (
    id bigserial PRIMARY KEY,
    created_at timestamp(0) with time zone NOT NULL DEFAULT NOW(),
    user_id bigint REFERENCES users ON DELETE CASCADE,
    title text NOT NULL,
    start_date date,
    days jsonb NOT NULL,
    version integer NOT NULL DEFAULT 1
);
