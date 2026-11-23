# TripWeaver

Personalised trip planning, itinerary optimisation and collaborative travel. Built for the Advanced Algorithms CIA (Semester V, 2026–27). Initial destination: Goa.

The full specification is in [`IMPLEMENTATION.md`](IMPLEMENTATION.md). Phase status is in [`docs/PROGRESS.md`](docs/PROGRESS.md).

## Prerequisites

- JDK 25 (`JAVA_HOME` must point to it; Maven uses `JAVA_HOME`)
- Node.js 24 and npm
- Docker (for PostgreSQL)

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

## Data attribution

Map data © OpenStreetMap contributors, available under the Open Database License (ODbL).
