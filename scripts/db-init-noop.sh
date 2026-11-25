# Mounted over the postgis image's /docker-entrypoint-initdb.d/10_postgis.sh so PostGIS is NOT preinstalled
# in "public". Flyway V6 installs it in the "extensions" schema (Supabase layout); PostGIS cannot be moved later.
