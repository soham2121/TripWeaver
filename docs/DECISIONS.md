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

## D18 — Recommendation scoring and selection cost
- **Decision:** Fixed, documented weights: score = 0.45 interest, 0.20 distance, 0.10 budget, 0.10 duration, 0.15 confidence. Set Cover cost = (0.2 + 0.4·duration + 0.2·spend + 0.2·detour)/(0.5 + relevance). Unknown spend uses a neutral 0.5 in ranking only, never in budget totals. Default seed 42; default K 60.
- **Why:** The values are simple, explainable in a viva, and every component is visible in the response.
- **Cost if wrong:** Ranking quality depends on the weights. They are constants in `ScoreBreakdown` and easy to tune.

## D19 — Set Cover picks one place per interest; scheduler fills the rest
- **Decision:** Set Cover guarantees diversity (each requested interest covered once, weighted). Extra activities for multi-day trips come from the ranked pool ("fill") during day allocation (Phase 6).
- **Why:** This keeps the classical set-cover objective intact instead of inventing a multicover variant.

## D20 — Stateless itinerary endpoint; trip persistence moves to Phase 9/10
- **Decision:** Phase 6 adds `POST /api/v1/itineraries/generate`. It persists only the `algorithm_runs` row. Re-planning is the same call carrying the user's edits: `keepStops`, `fixedStops`, `mustVisit` and `avoid`. The V4 trip tables (planned in the Phase 5 notes) move to Phase 9/10, where trips get owners, roles and optimistic locking.
- **Why:** Trips need an owner (Phase 10 auth) and a version column. A table added now would be rebuilt then. The planner itself is pure, so persistence can wrap it later without changes.
- **Cost if wrong:** A refresh loses an unsaved plan until Phase 9.

## D21 — Straight-line travel estimate until routing (Phase 7)
- **Decision:** Leg time = haversine × 1.3 road factor at an assumed speed per mode, plus a per-leg overhead. Car and taxi: 28 km/h + 5 min. Scooter: 25 km/h + 3 min. Walking: 4.5 km/h. Every response labels this in `travelBasis` as an estimate.
- **Why:** Phase 6 needs travel minutes to check day windows. Road routing (Q2) belongs to Phase 7.
- **Cost if wrong:** Hilly or congested legs are underestimated. The pace utilisation caps leave slack.

## D22 — Pace parameters
- **Decision:**

  | Pace | Max (visit + travel) / window | Buffer per stop | Meal | Max stops | Max HIGH-intensity | Visit length |
  |---|---|---|---|---|---|---|
  | RELAXED | 0.55 | 20 min | 75 min | 4 | 1 | top of range |
  | BALANCED | 0.70 | 15 min | 60 min | 6 | 1 | middle |
  | PACKED | 0.85 | 10 min | 45 min | 8 | 2 | bottom of range |

- The caps apply to optional stops only. Fixed bookings, must-visits and kept stops are bound only by the hard day window. The day label shown is derived from actual utilisation, so a packed day with few candidates can be labelled BALANCED.
- **Why:** These are simple and explainable values. They are constants in `Pace`.

## D23 — Opening hours: parsed subset, enforced where sourced
- **Decision:** `OpeningHours` parses `24/7`, weekday ranges and lists, multiple time ranges, `off`/`closed`, and rule override. Months, sunrise, `+` and comments are not parsed. Parsed hours from any sourced record (OSM PROVIDER_SOURCED or curated VERIFIED, with VERIFIED preferred) are hard constraints. Unparseable hours are labelled UNPARSED and places without hours are labelled UNKNOWN; both are scheduled with a warning.
- **Why:** Blocking all places with unknown hours would empty the plan (only 313 places have hours). Guessing hours is forbidden.

## D24 — Meals and food stops
- **Decision:** A lunch break (from 12:30) and, when the day reaches past 20:30, a dinner break are inserted at the first gap between stops. A restaurant, cafe or fast-food stop that starts between 30 minutes before the meal and its latest time (lunch 14:30, dinner 21:30) counts as that meal. At most 2 food stops per day; the variety penalty groups all eating places together.
- **Why:** Without this, the large OSM restaurant catalogue filled whole days with restaurants next to a separate lunch break.

## D25 — Budget model
- **Decision:** A per-person cost is multiplied by the party size; other bases (group, vehicle, item) are counted once. Food (per person per day), accommodation (per night) and fuel price and mileage are user-entered. Without them those lines are UNKNOWN, never zero. Fuel = road km × vehicles / mileage × price, labelled CURATED_ESTIMATE because the distance is an estimate. A hard budget is checked against the known **maximum**. Optional stops that would break it are skipped; mandatory stops that break it return 422 `BUDGET_INFEASIBLE`. Unknown items make the status INCOMPLETE. Taxi fares and tolls/parking are unknown (tolls belong to road trips, Phase 8).

## D26 — Trip-scaled candidate pool
- **Decision:** The planner asks the recommendation engine for K = 40 × days (max 500) with `quotaPerInterest` = 3 × days (max 15, still capped at K/|interests|). `fill` now returns the whole remaining pool, not only the first 40. The preview endpoint keeps K = 60 and quota 3.
- **Why:** With 3 reserved slots per interest, a 3-day Goa plan ran out of non-food candidates by day 3.

## D27 — Road routing with self-hosted OSRM (resolves Q2)
- **Decision:** The user chose OSRM. The setup:
  - **Image:** `ghcr.io/project-osrm/osrm-backend:v6.0.0`, car profile, Contraction Hierarchies.
  - **Network:** the whole Geofabrik western-zone extract (no cropping tool needed; the extract fits in memory).
  - **Service:** Docker Compose profile `routing`, host port 5001, `--max-table-size 1000`.
  - **Config:** the backend reads `OSRM_BASE_URL`, default `http://127.0.0.1:5001`.
- **Matrix:** each plan makes one `/table` call over the base plus all candidates. The result is directed: road times can differ by direction. Each leg adds the transport mode overhead (car 5 min), the same as the estimate.
- **Route:** one `/route` call per day for geometry and legs.
- **Fallback:** on any failure (not running, timeout, error code) the plan uses the labelled straight-line estimate and says so in `warnings`. Pairs OSRM cannot route use the estimate and are counted.
- **Walking:** always the estimate, since no foot profile is built.
- **Why CH, not MLD:** a 121-point table took 0.5 s on MLD, which dominated plan time.
- **Cost if wrong:**
  - OSRM car speeds are free-flow speeds; traffic is not modelled.
  - Scooter and taxi use car times.
  - The preprocessed files take about 3 GB of disk.

## D28 — Daily ordering: Double-Tree on symmetrised times, applied only if feasible and shorter
- **Decision:** after day allocation, each day is ordered by Double-Tree:
  - The input is the symmetrised matrix (d(i,j) + d(j,i)) / 2 over base and stops.
  - Closed tour when the day returns to base. Otherwise an open path from base to the current last stop; no bound is claimed for open paths.
  - `MetricValidator` decides whether the 2-approximation claim may be shown.
  - The new order is re-simulated with the real directed legs. It is used only if every hard constraint still holds and the symmetric tour is shorter than the insertion order. Otherwise the insertion order stays, and the response says why.
  - Nearest-neighbour and the exact Held–Karp optimum (days with ≤ 9 stops) are reported for comparison.
- **Why:** the cheapest-insertion schedule is often already near optimal. On the Goa demo it was within 1–6 min of the exact optimum, and replacing it with a longer tour would be worse for the user. The guarantee applies to the symmetric objective only, not to directed travel time.

## D29 — Konkan corridor as a second destination (`konkan`)
- **Decision:** Mumbai–Goa road trips need attractions and lodging outside Goa. A second destination is built by the same pipeline, with no algorithm changes:
  - **Extract:** `OsmExtractTool ... 15.60,72.75,19.10,74.10 -Goa 4`, giving `data/sample/konkan-osm-extract.json` (1.9 MB, ODbL). The area is the bbox minus the Goa boundary.
  - **Curation:** `data/curated/konkan-regions.json` defines 12 regions anchored at Mumbai, Panvel, Alibag, Lonavala, Mahad, Chiplun, Ratnagiri, Rajapur, Devgad, Malvan, Kudal and Sawantwadi. `konkan-curated.json` is empty.
  - **Import:** 6,283 places accepted, including 830 places to stay.
  - **Startup:** the runner imports every destination in `tripweaver.catalogue.destinations` (default `goa,konkan`) that has no places yet.
- **Why bbox minus Goa:** the Maharashtra relation cannot be assembled from the regional extract, because its far borders are clipped and the mainland ring never closes.
- **Polygon rule:** `Polygon` now means "odd number of outer rings and even number of inner rings" instead of a global even-odd count. Under even-odd, Goa land south of the bbox would have counted as inside.
- **Cost if wrong:** curated official facts (hours, fees) exist only for Goa; the corridor uses OSM tags only.

## D30 — Road-trip model and limits
- **Fixed waypoints:** origin, user waypoints and destination are never reordered.
- **Detours:**
  - Candidates are activities that match the interests and lie within max(5 km, 0.5 × `maxDetourMinutes` km) of the routed segment geometry, and more than 3 km from any waypoint.
  - They are ranked by the recommendation engine with no base location (neutral distance score).
  - Detours are accepted in rank order while each adds ≤ `maxDetourMinutes` of driving (default 45). The trip takes at most `maxDetours` detours (default 3), and their added driving plus visits stay ≤ `maxTotalDetourMinutes` (default 180).
  - Detours in one segment are ordered by the shorter of route order and a Double-Tree open path between the segment's fixed ends. No approximation bound is claimed.
  - Detours are optional. While the plan is infeasible, or needs more days than the plan without detours, the lowest-ranked detour is dropped, with a warning.
- **Driving rules and their defaults:**

  | Setting | Default |
  |---|---|
  | `maxDrivingHoursPerDay` | 7 |
  | `maxContinuousDrivingMinutes` | 120 |
  | `restBreakMinutes` | 15 |
  | Lunch break from 12:30 (`mealMinutes`) | 45 |
  | `dayStart` / `dayEnd` | 08:00 / 20:00 (hard) |
  | `departureTime` | 07:00 |

- **Overnight stops:**
  - A user waypoint with `overnight: true` ends the day there.
  - Otherwise, when the next leg does not fit, the planner suggests the OSM-listed place to stay within 5 km of the route that leaves the least driving and still fits today's limits.
  - Suggestions are labelled PROVIDER_SOURCED, with availability and price unknown. A place to stay can serve every segment it lies near.
- **Costs:**
  - Fuel is a CURATED_ESTIMATE from user-entered mileage and price.
  - Tolls, accommodation and food are USER_ENTERED, or UNKNOWN when not entered.
  - Taxi fares are UNKNOWN.
- **Stateless:** `POST /api/v1/road-trips/plan` only. The plan's `/detours` and `/segments` endpoints need saved trips and wait for Phase 9/10.

## D31 — Two-speed travel estimate
- **Decision:** the straight-line fallback drives the first 10 road-km of each leg at local speed (car 28, scooter 25 km/h) and the rest at highway speed (car and taxi 60, scooter 45 km/h).
- **Why:** with one speed, Mumbai→Goa estimated about 18 h. With two speeds it is about 9 h, against OSRM's free-flow 6.8 h.
- **Why 10 km:** a larger local share would wrongly penalise splitting a highway leg for a short detour.

## D32 — Editing an itinerary re-runs the planner
- **Decision:** every edit in the UI calls the stateless generate endpoint again with the user's edits. Nothing is patched locally, so times, travel, opening hours and budget always come from the planner.
- **How each edit maps to request fields:**

  | Edit | Request |
  |---|---|
  | Remove | `avoid` + the place; every other stop goes into `keepStops` on its day; `fillFreeTime: false` |
  | Replace | Same as remove, with `fillFreeTime: true`, so the planner suggests something else |
  | Move to day | Every stop in `keepStops`, the moved one on its new day; `fillFreeTime: false` |
  | Add a must-visit place | `mustVisit` + the place; current stops kept; `fillFreeTime: false` |
  | Lock + Replan | Only locked stops in `keepStops`; the rest is planned again |

- **Wizard:** generating a new plan from the wizard clears all edits.
- **New field:** `fillFreeTime` (default true) was added so that "remove" does not bring in a replacement.
- **Cost if wrong:** each edit is a full planning run, about 50 ms with OSRM on the Goa catalogue. Saved trips (Phase 10) will persist the edits.

## D33 — Frontend structure for Phase 9
- **Home:** three modes (`/plan?mode=today`, `/plan?mode=trip`, `/road-trip`) and four demo scenarios. The demos use the bundled catalogue and need no keys.
- **Today mode:** one day, starting at the next quarter hour (not before 09:00) and ending at 21:00. After 17:00 it plans tomorrow instead.
- **Wizard:** three steps: interests; where and when; travel and budget. "Generate plan" is available on every step once one interest is chosen.
- **Results:**
  - Provider notice: OSRM, or "road routing unavailable" with straight-line estimates.
  - Coverage gaps and warnings, plus what was left out or repaired.
  - Per-day workload bars (visits, travel, meals, buffer, free) and a budget table with a confidence badge on every line.
  - Day tabs, each with a map, the timeline including meals and travel, the ordering evidence and Google Maps links.
- **Road trip page:** fixed stops in the user's order, each with optional overnight and stop minutes. Place search covers the Konkan and Goa catalogues. Results show segments and detours, a timeline per day, the overnight with its confidence badge, and the budget.
- **No charting library:** workload bars are plain CSS with `role="meter"`.

## D34 — Sign-in: session cookie, no CSRF token
- **Login:** `POST /auth/login` checks a BCrypt hash. It stores an `AppUser` (id, username, display name; never the hash) in the session's security context and changes the session id to prevent session fixation.
- **Session cookie:** HttpOnly, SameSite=Lax, 12-hour timeout.
- **CSRF:** Spring's CSRF tokens stay off. The API accepts JSON only, and a SameSite=Lax cookie is not sent on cross-site POST, PATCH or DELETE.
- **Wrong credentials:** an unknown username and a wrong password both get the same 401 `BAD_CREDENTIALS`. The password check runs against a dummy hash for unknown users, so both cases take the same time.
- **Rate limit:** `/auth/login` and `/shared/**` are limited to 60 requests per minute per IP, then 429. The window is fixed and kept in memory, so it does not cover more than one backend instance.
- **Demo users:** asha, ravi and meera are created only when `DEMO_PASSWORD` is set. No password is stored in the repository.
- **Cost if wrong:** without CSRF tokens, a browser that ignores SameSite could be tricked into cross-site requests. Mitigation if needed: enable Spring's cookie CSRF token and send the header from `api()`.

## D35 — Collaboration model
- **Roles:**

  | Role | Can |
  |---|---|
  | Owner | Everything: rename, delete (archive), manage members, create and revoke share links |
  | Editor | Change the plan (PATCH or generate), suggest places, vote, add expenses |
  | Viewer | Read the trip, suggestions, expenses and history |

  Roles are checked in `TripService` on every call. Someone who is not on a trip gets 404, not 403.
- **Share links:**
  - The token is 256 bits from `SecureRandom`, base64url (43 characters). Only its SHA-256 is stored.
  - Links can be revoked and can expire.
  - The public view leaves out the planner request (which contains the owner's base location) and the member list.
- **Optimistic locking:** `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`. When no row matches, the call returns 409 with the current version. The UI then shows the message and loads the latest trip, so nothing is silently overwritten.
- **Votes:** at most one per person per suggestion, enforced by the primary key. The same vote again returns 409 `DUPLICATE_VOTE`; the opposite vote changes it.
- **Expenses:** split equally among everyone on the trip (owner and members). Balance = paid − share.
- **Live updates:** the trip page polls every 15 s and shows a companion's newer version when it is idle. No WebSockets (plan 8.2 says polling is enough for the MVP).
- **Storage:** trips store the planner request and result as JSON text. Planning stays pure, and a saved trip can be planned again with the stored request.
- **Column name:** `activity_suggestions.day_number`, because `day` is reserved in H2, the test database.
