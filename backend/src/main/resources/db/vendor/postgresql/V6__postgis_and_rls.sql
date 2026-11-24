-- PostgreSQL only (Supabase and the local docker-compose PostGIS image). Not run on H2.

-- PostGIS lives in the dedicated "extensions" schema (Supabase convention; already there on Supabase).
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;

-- Point geography derived from latitude/longitude, so importers keep writing plain columns and the two
-- can never disagree. Geography distances are in metres on the WGS84 spheroid.
ALTER TABLE places ADD COLUMN geog extensions.geography(Point, 4326)
    GENERATED ALWAYS AS (
        extensions.ST_SetSRID(extensions.ST_MakePoint(longitude, latitude), 4326)::extensions.geography
    ) STORED;
CREATE INDEX ix_places_geog ON places USING GIST (geog);

-- The backend connects as the table owner and enforces permissions itself (IMPLEMENTATION.md 8.2).
-- Supabase also exposes the public schema through its Data API to the anon and authenticated roles,
-- so lock that path: RLS on every table with no policies (deny all), and no grants for those roles.
-- New tables in later migrations must also run ENABLE ROW LEVEL SECURITY.
DO $$
DECLARE
    t RECORD;
BEGIN
    -- flyway_schema_history is locked by Flyway while this runs; afterMigrate.sql covers it.
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
    END IF;
END $$;
