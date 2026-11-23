# Progress

Phase status and recent work. A phase is "passed" only when its acceptance gate was verified with commands actually run.

| Phase | Status |
|---|---|
| 0 — Inspection, scope lock, decisions | Passed |
| 1 — Skeleton and tooling | Not started |
| 2 — Core algorithms | Not started |
| 3 — Place catalogue and data | Not started |
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
