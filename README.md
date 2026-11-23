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

Algorithm details: [`docs/ALGORITHM_DESIGN.md`](docs/ALGORITHM_DESIGN.md). Tests: [`docs/CORRECTNESS.md`](docs/CORRECTNESS.md). API: [`docs/API_SPEC.md`](docs/API_SPEC.md). Decisions: [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Prerequisites

- JDK 25. `JAVA_HOME` must point to it, because Maven uses `JAVA_HOME`.
- Node.js 24 and npm
- Docker (for PostgreSQL, and optionally OSRM)

## Run locally

```bash
# 1. Database (PostgreSQL 17 on host port 5433)
docker compose up -d db

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

Configuration comes from environment variables. See [`.env.example`](.env.example). No API keys are needed for the core workflow.

## Tests and checks

```bash
cd backend && ./mvnw verify        # tests + Spotless format check (no Docker needed; tests use in-memory H2)
cd backend && ./mvnw spotless:apply # auto-format Java

cd frontend && npm test            # Vitest + React Testing Library
cd frontend && npm run lint        # oxlint
cd frontend && npm run format      # Prettier
cd frontend && npm run build       # type-check + production build
```

Current suite: 206 backend tests and 34 frontend tests.

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
