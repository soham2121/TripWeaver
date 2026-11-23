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
