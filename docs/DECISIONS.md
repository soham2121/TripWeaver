# Decisions

Material technical choices. Newest at the bottom. Each entry: decision, reason, cost if wrong.

## D1 — Java 25 instead of Java 21
- **Decision:** Build and run on JDK 25 (Temurin 25.0.1, installed). `IMPLEMENTATION.md` updated to say Java 25.
- **Why:** Team instruction (2026-11-23): machine has JDK 25, not 21; build everything on 25.
- **Cost if wrong:** Examiner machine needs JDK 25.

## D2 — Spring Boot 4.1.x, Maven
- **Decision:** Spring Boot 4.1.3 (supports Java 25), Maven 3.9.
- **Why:** Latest stable line with Java 25 support at project start.

## D3 — Node 24 + npm, React 19 + Vite + TypeScript + Tailwind
- **Decision:** npm (installed). No pnpm/yarn.

## D4 — PostgreSQL 17 via docker-compose; H2 (PostgreSQL mode) for automated tests
- **Decision:** Runtime DB is PostgreSQL in Docker. Backend test suite uses in-memory H2 in PostgreSQL compatibility mode so `mvn test` works from a clean checkout without Docker.
- **Why:** Plan requires tests to run from a clean checkout. Flyway migrations stay in portable SQL.
- **Cost if wrong:** A Postgres-only SQL bug could pass tests. Mitigation: app startup against real Postgres is part of each phase gate check.

## D5 — Persistence: Spring Data JPA + Flyway migrations

## D6 — Authentication: username + password
- **Decision:** Spring Security, BCrypt hashes, session cookie. Seeded demo users. No email, no OAuth.
- **Why:** Team choice (2026-11-23). Needed for owner/editor/viewer permissions.

## D7 — Git
- **Decision:** Local repo, branch `dev`, one commit per passed phase gate. No remote, no push.
- **Why:** Team choice (2026-11-23).

## D8 — Modular monolith
- **Decision:** One Spring Boot app, packages per module (plan §9.2). No Redis, no microservices.

## D9 — Local ports: API 8088, PostgreSQL host port 5433
- **Decision:** Backend defaults to port 8088; Docker Postgres maps to host port 5433; JDBC URL uses `127.0.0.1`.
- **Why:** On the dev machine, port 8080 is taken by a Jenkins service and `[::1]:5432` by a WSL relay (another Postgres). Overridable via `PORT` and `DB_URL`.

## D10 — Formatting and linting
- **Decision:** Java: Spotless + Palantir Java Format, checked in `mvn verify`. Frontend: oxlint (Vite template default) + Prettier.

## D11 — OSM import pipeline in Java, derived Goa extract committed
- **Decision:** `OsmPbfExtractor` (osmpbf 1.8.0 library for PBF decoding only) streams the Geofabrik western-zone PBF in three passes into `data/sample/goa-osm-extract.json` (1.2 MB, ODbL). `CatalogueBuilder` (pure) turns it into catalogue rows. `CatalogueWriter` upserts with deterministic UUIDs. The app auto-imports when the `places` table is empty.
- **Why:** The demo must work offline without downloading 222 MB. ODbL permits redistributing the derived database with attribution under the same licence. The raw PBF stays out of Git.
- **Cost if wrong:** Re-importing a newer extract changes the counts. Ids stay stable for places that still exist.

## D12 — Travel regions by nearest anchor settlement
- **Decision:** 12 curated regions, each anchored at an OSM settlement. A place joins the nearest anchor (a Voronoi-style assignment).
- **Why:** India's OSM admin levels below the state are inconsistent. Travel regions differ from talukas anyway.
- **Cost if wrong:** Places near region borders can land in a neighbouring region. Grouping is a planning aid, not a hard constraint.

## D13 — Places of worship need a notability signal
- **Decision:** Keep `amenity=place_of_worship` only with historic, heritage, wikidata, wikipedia or tourism=attraction tags.
- **Why:** Without this filter, thousands of small chapels and shrines flood the heritage and spiritual candidates.

## D14 — Visit durations are category defaults
- **Decision:** Visit duration ranges come from category defaults, labelled CURATED_ESTIMATE ("TripWeaver category default for X"). No per-place durations are invented.

## D15 — JDBC instead of JPA
- **Decision:** Persistence uses Spring JDBC (`JdbcTemplate`) with explicit SQL and Flyway. The JPA starter was removed.
- **Why:** The catalogue import is bulk batch work. The read models are DTO-shaped. The plan allows "JPA or JDBC". Optimistic locking will use an explicit `version` column.

## D16 — Map tiles: OpenFreeMap (resolves Q3)
- **Decision:** MapLibre GL with the OpenFreeMap "liberty" style (`https://tiles.openfreemap.org/styles/liberty`). No API key. MapLibre renders the attribution from the style.
- **Why:** OpenFreeMap is free with no key and allows this use with attribution. If tiles fail, the page falls back to the list view.

## D17 — In-memory search index
- **Decision:** `PlaceCatalogue` holds an immutable `PlaceSearchIndex` (Java Trie plus a sorted list) per destination. It is rebuilt after each catalogue import. Nearby search is a linear haversine scan.
- **Why:** About 5K places fit easily in memory. The Trie is the assessed component. A spatial index would not pay off at this size.
