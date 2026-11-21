BEGIN;
-- Serialize schema creation when several application instances start together.
SELECT pg_advisory_xact_lock(713064923);
CREATE TABLE IF NOT EXISTS input_dataset (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload TEXT NOT NULL
);
COMMIT;
