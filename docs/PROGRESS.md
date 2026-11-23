# Progress

Phase status and recent work. A phase is "passed" only when its acceptance gate was verified with commands actually run.

| Phase | Status |
|---|---|
| 0 — Inspection, scope lock, decisions | Passed |
| 1 — Skeleton and tooling | Passed |
| 2 — Core algorithms | Passed |
| 3 — Place catalogue and data | Passed |
| 4 — Discovery and search | Not started |
| 5 — Recommendation and selection | Not started |
| 6 — Time, budget, pace, scheduling | Not started |
| 7 — Route ordering and navigation | Not started |
| 8 — Road-trip planner | Not started |
| 9 — Itinerary UI and editing | Not started |
| 10 — Sharing and collaboration | Not started |
| 11 — Optional enrichment | Not started |
| 12 — Benchmarks and audit | Not started |
| 13 — Submission package | Not started |

---

## Phase 0 report

- **Repository found:** only the plan (`Docs/implementation.md`). No code to preserve. Plan moved to `IMPLEMENTATION.md` at repo root (plan §9.2). `docs/` created lowercase.
- **Environment (checked 2026-11-23):**
  - JDK: Temurin 25.0.1 (`JAVA_HOME`), Maven 3.9.14. `java` on PATH is 17; Maven uses `JAVA_HOME` (25).
  - Node v24.14.1, npm 11.11.0.
  - Docker 29.2.1 (Docker Desktop), git 2.49.0. No local `psql`; PostgreSQL runs in Docker.
- **Local commands (target, created in Phase 1):**
  - `docker compose up -d db` — PostgreSQL.
  - `cd backend && mvn spring-boot:run` — API on :8080.
  - `cd backend && mvn test` — backend tests (no Docker needed).
  - `cd frontend && npm install && npm run dev` — UI on :5173 (proxies `/api` to :8080).
  - `cd frontend && npm test` — frontend tests.
- **Optional credentials:** `GOOGLE_PLACES_API_KEY` (Phase 11, optional). Core app needs none.
- **Data providers and licence risks:** OSM via Geofabrik (ODbL: attribution, share-alike on derived DB); Google Places (optional, strict storage rules, no bulk storage); OpenFreeMap/OSM tiles (attribution, usage policy); OSRM (BSD, self-hosted); official tourism sites (field-level source URL + date).
- **Secrets/raw data excluded:** `.gitignore` covers `.env*`, `data/raw/`, `data/processed/`.

## Phase 1 report — Skeleton and tooling (passed)

- **Created:** `backend/` (Spring Boot 4.1.3, Java 25, Flyway, JPA, Security, Validation, Actuator), `frontend/` (React 19, Vite 8, TypeScript, Tailwind 4, Vitest, RTL, oxlint, Prettier), `docker-compose.yml`, `.env.example`, `.gitattributes`, `.github/workflows/ci.yml`, `README.md`.
- **Health endpoint:** `GET /api/v1/health`. **Error format:** `ApiError {errorCode, message, details}` via `GlobalExceptionHandler`; no stack traces.
- **Commands run and results:**
  - `mvn verify` (backend): 3 tests, 3 passed; Spotless check passed.
  - `npm test` (frontend): 2 tests, 2 passed. `npm run lint`: no findings. `npm run build`: success. `npm run format:check`: clean.
  - Backend jar against Docker PostgreSQL: Flyway applied V1; `curl localhost:5173/api/v1/health` (through Vite proxy) returned `{"status":"UP",...}`.
- **CI:** workflow written; not executed (no remote).
- **Security note:** `SecurityConfig` permits all requests until Phase 10 adds login and trip permissions. No protected data exists yet.

## Phase 2 report — Core algorithms (passed)

- **Implemented (plain Java, `backend/src/main/java/com/tripweaver/algorithms/`):**
  - `trie/` — `Trie`, `LinearScanIndex` (baseline)
  - `selection/` — `RandomizedQuickselect`, `CategoryAwareSelector`
  - `heap/` — `IndexedMaxHeap`
  - `setcover/` — `GreedyWeightedSetCover`, `LazyGreedySetCover`, `ExactSetCover` (reference)
  - `tsp/` — `MetricValidator`, `DoubleTreeTsp`, `ExactTsp` (Held–Karp), `NearestNeighbourTsp`
- **Process:** test classes written first; first run failed to compile because the classes did not exist (RED). Then each algorithm was implemented and its tests run to GREEN.
- **Command run:** `mvn verify`, result 68 tests, 0 failures, 0 errors; Spotless check passed.
  - Trie 12, Quickselect 15, Set Cover 15 (including lazy = full on 400 random instances, H(d) bound vs exact on 300), Heap 10, TSP 12 (2-approximation vs Held–Karp on 150 instances), isolation 1, health API 3.
- **Docs:** `docs/ALGORITHM_DESIGN.md` (pseudocode, proofs, complexity, tie rules), `docs/CORRECTNESS.md` (invariants and test map).
- **Gate:** all tests pass; edge cases defined; Quickselect matches sorting; Set Cover output valid; heap invariant holds after every operation; lazy greedy matches reference; TSP assumptions validated and approximation checks pass.

## Phase 3 report — Place catalogue and real data (passed)

**Checklist**
- [x] Place, Destination and Region models: Flyway `V2__place_catalogue.sql`, with field-level source, cost, hours and import-batch tables.
- [x] Downloaded Geofabrik `western-zone-261122.osm.pbf`; MD5 verified.
- [x] OSM import: `OsmPbfExtractor` → `OsmExtract`; `OsmExtractTool` CLI.
- [x] Categories and tags normalised (`OsmCategoryMapper`, 14-interest taxonomy).
- [x] Points of interest imported: 5,192 accepted.
- [~] Map geometry and routing data: Goa boundary polygon imported; PBF downloaded for OSRM. OSRM preprocessing happens in Phase 7.
- [x] Destination and neighbourhood mapping: 12 regions; neighbourhood = nearest settlement within 3 km.
- [x] Deduplication: stable OSM ids; same-name, same-category features within 300 m merged (67 merged).
- [x] Curated Goa data from official sources (4 records; 1 unresolved and reported).
- [x] Field-level source and verification timestamps (`place_source_records`).
- [x] Duration and cost confidence fields.
- [x] Coordinates validated against the Goa boundary polygon (70 rejected).
- [x] Accepted/rejected/merged/unresolved report (`data/processed/goa-import-report.json`, `place_import_batches`).
- [x] Licensed sample fixture: `data/sample/goa-osm-extract.json` (ODbL notice).
- [x] OSM attribution and licensing documented (`docs/DATA_SOURCES.md`, README).
- [x] Coverage report written (`docs/DATA_SOURCES.md` §5).

**Commands and results**
- `mvn verify`: 94 tests, 0 failures, 0 errors. New tests: `OsmCategoryMapperTest` 9, `GeoTest` 5, `CatalogueBuilderTest` 11, `GoaCatalogueImportTest` 1 (real extract imported twice; report and ids identical).
- `OsmExtractTool` on the real PBF: 5,329 features, 1,227 settlements, 11 boundary rings, 11.4 s.
- App started against Docker PostgreSQL: Flyway V2 applied; auto-import gave 5,192 places, 3,683 of them activities, in 12 regions.

**Bug found and fixed (test first):** `isSettlement` threw a NullPointerException on named nodes without a `place` tag.

**Known gaps:** see `docs/DATA_SOURCES.md` §5. Cost is known for only 6 activities. Opening hours exist for 313 places. Costs stay unknown and are never treated as zero.

**Gate:** catalogue loads reproducibly (verified twice in a test and once on Postgres); every place has OSM provenance; missing data is flagged per place; no review data stored.
