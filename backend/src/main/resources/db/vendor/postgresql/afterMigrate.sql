-- Runs after every migrate (PostgreSQL only). Idempotent.
-- Flyway's own history table lives in public, which Supabase exposes through its Data API.
-- It cannot be altered inside a migration (Flyway holds a lock on it), so lock it down here.
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON flyway_schema_history FROM anon, authenticated;
    END IF;
END $$;
