-- Read-only database checks after migration/import. One row per check; every "ok" must be true.
-- Supabase:  supabase db query --linked -f scripts/verify-db.sql   (after supabase link)
-- Local:     docker exec -i tripweaver-db-1 psql -U tripweaver -d tripweaver < scripts/verify-db.sql
SELECT 'flyway at V6 or later, no failed migrations' AS check_name,
       (SELECT MAX(version::int) FROM flyway_schema_history WHERE success) >= 6
       AND NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success) AS ok,
       (SELECT string_agg(version || ':' || success, ' ' ORDER BY installed_rank) FROM flyway_schema_history) AS detail
UNION ALL
SELECT 'postgis installed in extensions schema',
       EXISTS (SELECT 1 FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
               WHERE e.extname = 'postgis' AND n.nspname = 'extensions'),
       (SELECT extversion FROM pg_extension WHERE extname = 'postgis')
UNION ALL
SELECT 'RLS enabled on every public table',
       NOT EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND NOT relrowsecurity),
       (SELECT string_agg(relname, ',') FROM pg_class
        WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND NOT relrowsecurity)
UNION ALL
SELECT 'anon/authenticated have no table privileges in public',
       NOT EXISTS (SELECT 1 FROM information_schema.role_table_grants
                   WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated')),
       (SELECT string_agg(DISTINCT grantee || ':' || table_name, ',') FROM information_schema.role_table_grants
        WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated'))
UNION ALL
SELECT 'every place has geography; GiST index exists',
       NOT EXISTS (SELECT 1 FROM places WHERE geog IS NULL)
       AND EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ix_places_geog'),
       (SELECT COUNT(*)::text || ' places' FROM places)
UNION ALL
SELECT 'catalogue loaded per destination',
       (SELECT COUNT(DISTINCT destination_id) FROM places WHERE is_active) >= 1,
       (SELECT string_agg(d.slug || '=' || c, ' ') FROM
          (SELECT destination_id, COUNT(*) c FROM places WHERE is_active GROUP BY 1) x
          JOIN destinations d ON d.id = x.destination_id)
UNION ALL
SELECT 'no Google content stored (D39)',
       (to_regclass('public.external_place_references') IS NULL
        OR (xpath('/row/c/text()', query_to_xml('SELECT COUNT(*) AS c FROM public.external_place_references', false, true, '')))[1]::text = '0')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND column_name ILIKE '%google%'
                       AND column_name <> 'google_place_id')
       AND (NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'places' AND column_name = 'google_place_id')
            OR (xpath('/row/c/text()', query_to_xml('SELECT COUNT(*) AS c FROM public.places WHERE google_place_id IS NOT NULL', false, true, '')))[1]::text = '0'),
       NULL
UNION ALL
SELECT 'database size (Free plan limit 500 MB)', pg_database_size(current_database()) < 400 * 1024 * 1024,
       pg_size_pretty(pg_database_size(current_database()));
