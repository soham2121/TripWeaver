# TripWeaver

Personalised trip planning, itinerary optimisation and collaborative travel. Built for the Advanced Algorithms CIA (Semester V, 2026–27). Destinations: Goa, plus the Mumbai–Goa (Konkan) corridor for road trips.

The full specification is in [`IMPLEMENTATION.md`](IMPLEMENTATION.md). Phase status is in [`docs/PROGRESS.md`](docs/PROGRESS.md).

## What it does

- **Discover:** search about 5,200 real Goa places (OpenStreetMap plus curated official facts) with Trie autocomplete, filters and a map.
- **Plan today / multi-day trip:** a wizard collects interests, base, dates, pace, transport and budget. The planner then:
  1. Ranks places with Randomized Quickselect.
  2. Picks a diverse set with Greedy Weighted Set Cover, driven by an Indexed Max Heap.
  3. Schedules days with opening hours, meals and budget.
  4. Orders each day with Metric TSP Double-Tree.

  You can lock, remove, replace, move or add stops and replan.
- **Road trip:** fixed stops in your order, optional bounded detours, driving-day limits with rests, and suggested overnight stays.
- **Share and collaborate:** save trips, then share with others.
  - Invite companions as editors or viewers.
  - Share a revocable read-only link.
  - Suggest places and vote, split expenses, and see the change history.
  - Edits made at the same time are protected by optimistic locking.
- **Honest labels:** every cost and fact carries its source and confidence. Unknown costs are never shown as ₹0.
- **Light and dark themes:** follows the system setting; the header toggle remembers your choice.

Submission: [`docs/PROPOSAL.md`](docs/PROPOSAL.md), [`docs/REPORT.md`](docs/REPORT.md), [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md), screenshots in [`docs/screenshots/`](docs/screenshots/).

Algorithm details: [`docs/ALGORITHM_DESIGN.md`](docs/ALGORITHM_DESIGN.md). Tests: [`docs/CORRECTNESS.md`](docs/CORRECTNESS.md). API: [`docs/API_SPEC.md`](docs/API_SPEC.md). Decisions: [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Prerequisites

- JDK 21 or newer (the build targets Java 21). `JAVA_HOME` must point to it, because Maven uses `JAVA_HOME`.
- Node.js 24 and npm
- Docker (for the local PostGIS database, and optionally OSRM). Not needed for the database when you use Supabase.

## Run locally

```bash
# 1. Database: EITHER local PostgreSQL 17 + PostGIS on host port 5433
docker compose up -d db
#    OR hosted Supabase: copy .env.example to .env and set SPRING_DATASOURCE_* (see "Supabase" below)

# 2. Optional road routing (OSRM on host port 5001). One-off preprocessing, about 10 minutes:
#    needs data/raw/western-zone-261122.osm.pbf (see docs/DATA_SOURCES.md)
sh scripts/osrm-prepare.sh
docker compose --profile routing up -d osrm
#    Without OSRM the planner uses labelled straight-line travel estimates.

# 3. Backend API on http://localhost:8088
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 4. Frontend on http://localhost:5173 (proxies /api to :8088)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.
- **Home page:** three modes (plan today, multi-day trip, road trip) and preloaded demo scenarios. They work without any API keys.
- **Accounts:** use **Sign in** to create an account. You need one to save trips, invite companions and share a read-only link.
- **Demo users:** set `DEMO_PASSWORD` before starting the backend to get asha, ravi and meera.

**First start:** the backend imports the Goa and Konkan catalogues from `data/` on an empty database, which takes a few seconds. Requests made during the import can see an incomplete catalogue, so wait for the `Catalogue import goa` and `Catalogue import konkan` log lines before the first plan.

Configuration comes from environment variables or a `.env` file at the repo root (read by the backend; real environment variables win). See [`.env.example`](.env.example). No API keys are needed for the core workflow.

### Supabase (hosted database)

1. In the Supabase dashboard open **Connect → Session pooler** (IPv4). The direct `db.<ref>.supabase.co` host is IPv6-only on the Free plan.
2. Copy `.env.example` to `.env` and uncomment the three `SPRING_DATASOURCE_*` lines: JDBC URL `jdbc:postgresql://<pooler host>:5432/postgres?sslmode=require`, user `postgres.<project-ref>`, and the database password. Never commit `.env`.
3. Start the backend. Flyway creates the schema (V1–V7: catalogue, PostGIS geography column and GiST index, RLS on every table). The first start imports the Goa and Konkan catalogues (about 11,500 places, about 35 MB).
4. Check: `supabase link --project-ref <ref>`, then `supabase db query --linked -f "$PWD/scripts/verify-db.sql"` (the CLI resolves relative paths against its workdir) (every `ok` must be true) and `supabase db advisors --linked`.

Rebuild or move the catalogue: it is derived entirely from files in Git (`data/sample`, `data/curated`), so an empty database rebuilds it on start. `tripweaver.catalogue.reimport=true` forces a re-import (idempotent upsert by OSM id). User and trip data can be exported with `supabase db dump --linked --data-only`.

### Hosting: Vercel (frontend) + Render (API)

Vercel serves the React app. The Java API runs on Render as a Docker web service, using the same Supabase database. Vercel forwards `/api/*` to Render, so the browser sees one origin and the session cookie works.

- **Vercel:** the project builds `frontend/` from the `dev` branch. `frontend/vercel.json` rewrites `/api/*` to the Render URL and every other path to `index.html`, so deep links such as `/shared/<token>` work.
- **Render:** web service, runtime Docker, Dockerfile path `backend/Dockerfile`, Docker context `.` (repo root), branch `dev`, health check `/api/v1/health`.
  - Environment: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, the same values as in `.env`.
  - The image already sets `OSRM_BASE_URL=""` (labelled estimates, no OSRM), `DB_POOL_SIZE=3` and the data directory.
  - The free plan sleeps after 15 minutes without traffic, and the first request then takes about a minute. Open the site shortly before a demo.
- **Limits of the hosted version:**
  - There is no road routing; travel times are labelled estimates.
  - The sign-in and share-link rate limits count all visitors together, because every request arrives from Vercel's proxy.

### Maps, routing and navigation (no Google API)

- **Map tiles:** MapLibre with OpenFreeMap's OSM-based style by default. Set `VITE_MAP_STYLE_URL` in `frontend/.env.local` to use another provider (see `frontend/.env.example`).
- **Road routing:** self-hosted OSRM. Without it the app says "Road routing unavailable" and labels travel times as straight-line estimates.
- **Navigation:** "Open in Google Maps" links are plain directions URLs built from coordinates. No Google API, key or account is involved, and TripWeaver reads nothing back.
- TripWeaver does not use the Google Places API (decision D39).

## Tests and checks

```bash
cd backend && ./mvnw verify        # tests + Spotless format check (no Docker needed; tests use in-memory H2)
cd backend && ./mvnw spotless:apply # auto-format Java

cd frontend && npm test            # Vitest + React Testing Library
cd frontend && npm run lint        # oxlint
cd frontend && npm run format      # Prettier
cd frontend && npm run build       # type-check + production build
```

Current suite: 216 backend tests and 36 frontend tests.

## Project layout

```
backend/src/main/java/com/tripweaver/
  algorithms/      Trie, Randomized Quickselect, Weighted Set Cover, Indexed Max Heap, Double-Tree TSP (own Java code)
  place/, search/  Catalogue import (OSM + curated) and the search index
  recommendation/  Ranking and diverse selection
  planning/        Day scheduling, opening hours, pace, budget
  routing/         OSRM client, estimate fallback, Google Maps links
  roadtrip/        Road-trip planner
  auth/, trip/     Accounts, saved trips, roles, sharing, suggestions, expenses, history
frontend/src/features/  home, discover, plan, roadtrip, trips, auth
data/              Sample OSM extracts (ODbL) and curated files
docs/              Design, decisions, API, data, correctness, benchmarks, progress
```

## Data attribution

Map data © OpenStreetMap contributors, available under the Open Database License (ODbL). Map tiles: OpenFreeMap.
