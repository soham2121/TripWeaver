# Progress

Phase status and recent work. A phase is "passed" only when its acceptance gate was verified with commands actually run.

| Phase | Status |
|---|---|
| 0 — Inspection, scope lock, decisions | Passed |
| 1 — Skeleton and tooling | Passed |
| 2 — Core algorithms | Passed |
| 3 — Place catalogue and data | Passed |
| 4 — Discovery and search | Passed |
| 5 — Recommendation and selection | Passed |
| 6 — Time, budget, pace, scheduling | Passed |
| 7 — Route ordering and navigation | Passed |
| 8 — Road-trip planner | Passed |
| 9 — Itinerary UI and editing | Passed |
| 10 — Sharing and collaboration | Passed |
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

## Phase 4 report — Discovery and search (passed)

**Checklist**
- [x] Place search API: `GET /places/search` (q, category, interest, region, activitiesOnly, paging).
- [x] Java Trie autocomplete: `PlaceSearchIndex` uses `algorithms.trie.Trie` (names plus word suffixes, categories, interests, cuisines, neighbourhoods, regions); `GET /places/autocomplete`.
- [x] Category and interest filters.
- [x] Destination, neighbourhood and region filters.
- [x] Nearby lookup: `GET /places/nearby`.
- [x] Place detail screen (`PlaceDetailPanel`) with costs, hours, seasonal note, sources, OSM link and attribution.
- [x] Source and confidence labels (`ConfidenceBadge`). Unknown cost is shown as "Cost unknown", never ₹0.
- [x] Map and list view (MapLibre + OpenFreeMap; list fallback when tiles fail).
- [x] Autocomplete UI (combobox, keyboard navigation).
- [x] Trie vs linear-scan benchmark at 1K/10K/100K/1M (`docs/BENCHMARKS.md`).
- [x] API and integration tests.

**Commands and results**
- `mvn verify`: 108 tests, 0 failures. New tests: `PlaceSearchIndexTest` 6, `PlaceApiTest` 8 (real Goa catalogue, no credentials).
- `npm test`: 8 passed. `npm run build`: success. `npm run lint`: 0 errors, 2 warnings (setState in data-fetch effects).
- Benchmark run: `BenchmarkMain trie`. Raw CSV committed; results in `docs/BENCHMARKS.md`.
- Live smoke test (jar on Postgres plus Vite proxy): autocomplete `calang` returned the Calangute neighbourhood first, then places. `q=fort` returned real forts. Heritage filter in `panaji-old-goa` worked. OpenFreeMap style URL returned 200.
- Visual browser check: **not done**, because the Chrome extension was not connected. The UI is covered by component tests only.

**Fixes found during the phase**
- `LinearScanIndex.insert` was O(N) per insert, which made the 1M-term benchmark setup O(N²). Duplicate check now uses a hash set; Trie tests still pass.
- Facet suggestions now come before places in autocomplete (API test caught an exact-name place ranked above its neighbourhood).

**Gate:** discovery by prefix, category and area works; the Trie runs in Java on the backend; no Google credentials are used anywhere.

## Phase 5 report — Recommendation and experience selection (passed)

**Checklist**
- [x] Interest taxonomy (14 tags, `OsmCategoryMapper.INTERESTS`).
- [x] User interest weights in (0, 5].
- [x] Transparent relevance score with per-component breakdown (`ScoreBreakdown`).
- [x] Hard filters separated from soft scores (avoid, regions, seasonal closure, wheelchair; must-visit conflicts → 422).
- [x] Category-aware Quickselect (per-interest quotas).
- [x] Weighted Set Cover with the binary-heap lazy greedy (full greedy run alongside as a check).
- [x] Rare interests preserved (tested: water_sports next to food).
- [x] Selection explanations (reason, newly covered, gain/cost).
- [x] Covered and uncovered interests returned with warnings.
- [x] Ties (total order on id) and infeasibility (422 with relaxations).
- [x] Tested against the real Goa catalogue (`RecommendationApiTest`).
- [x] Ranking-only baseline compared in every response.
- [x] Small Set Cover instances compared with the exact solution (Phase 2 tests, 300 instances).
- [x] Algorithm metadata and seed persisted (`algorithm_runs`, Flyway V3; `GET /algorithms/metadata`).

**Commands and results**
- `mvn verify`: 128 tests, 0 failures. New tests: `RecommendationEngineTest` 14, `RecommendationApiTest` 6.
- `npm test`: 8 passed. Build and format check clean (types updated for the new summary fields).
- Live Postgres check: Flyway V3 applied. Demo request result is in `docs/ALGORITHM_DESIGN.md` (all 4 interests covered; baseline left 3 uncovered; heap 118 vs full 234 gain evaluations).

**Bug found and fixed:** a request body without `requireWheelchair` failed with 400 because Jackson 3 rejects null for a primitive `boolean`. The field is now a `Boolean`, normalised to false. Bad-request causes are now logged at DEBUG.

**Gate:** preferences change results (tested); selection is diverse (Set Cover covers every coverable interest); exclusions are respected; uncovered interests are visible; results are reproducible from the seed (same seed gives an equal result; any seed gives the same picks).

**Next phase prerequisites (Phase 6):** the trip model (V4 tables `trips`, `trip_days`, `itinerary_stops`), budget model, workload-based pace, and a day-allocation heuristic consuming `selected` + `fill`.

## Phase 6 report — Time, budget, pace and multi-day scheduling (passed)

**Checklist:** all 17 items done (see `IMPLEMENTATION.md`).
- **Budget:** total or per-person budget with party-size conversion (D25). Each cost keeps its basis and confidence. Unknown cost stays null and is listed in `unknownItems`, never zero.
- **Durations and windows:** visit duration ranges come from the pace (D22), inside daily time windows with per-day overrides (start, end, pace, return to base).
- **Meals and buffers:** meal breaks are inserted between stops, or a food stop at mealtime counts as the meal (D24). Each pace sets a transition buffer.
- **Opening hours:** parsed OSM syntax is enforced against the weekday of each trip date. Curated seasonal closures are checked per day.
- **Workload:** each day reports window, activity, travel, meal, buffer, wait and free minutes, utilisation, stop count, high-intensity stops, region transitions, intensity load and a derived label.
- **Scheduling:** `ItineraryPlanner`, a greedy insertion heuristic (plan §5.6). Fixed bookings come first, then kept stops, then must-visits, then optional stops ranked by uncovered-interest gain and relevance per minute of added burden. Moving to another region costs a 30-minute penalty. Repeated categories are penalised.
- **Repair:** a bounded pass drops up to 3 lowest-value non-mandatory stops from one day to fit a must-visit. A second pass drops the lowest-value optional stops until the known maximum fits a hard budget. Every repair is reported.
- **Unplanned items:** set-cover picks that could not be scheduled and removed stops are listed with reasons. Each uncovered interest is explained. Hard constraint conflicts return 422 `INFEASIBLE_PLAN` or `BUDGET_INFEASIBLE` with figures and suggested relaxations.
- **Pace tests:** relaxed, balanced and packed plans are tested; relaxed has more free time and fewer stops.

**New:**
- API: `POST /api/v1/itineraries/generate`. Re-planning is the same call with edits (D20).
- Catalogue: opening hours are loaded per place (VERIFIED preferred).
- Recommendation engine: `quotaPerInterest`; `fill` returns the whole pool (D26).
- Logging: one `algorithm_runs` row per plan, `greedy-day-insertion+bounded-repair`, with workload, repairs and insertion evaluations.

**Commands and results:**
- `./mvnw -o verify`: 158 tests, 0 failures, 0 errors.
  - New tests: `OpeningHoursTest` 8, `ItineraryPlannerTest` 16, `ItineraryApiTest` 5, plus one in `RecommendationEngineTest`.
  - The final repackage step failed only because the running local server held the jar (Windows file lock).
  - `spotless:check` is clean, and `package` succeeded after the server was stopped.
- Live check against Postgres. Request: Candolim base, 3 days from 2026-12-14, party of 2, beach 5 / food 4 / heritage 4 / adventure 2, flexible ₹30,000, food ₹1,200 per person per day, ₹4,000 per night, fuel ₹101/l at 15 km/l.
  - Result: 62 ms; 16 stops including beaches, Kaurati's Caves, Fort Aguada, Aguada Jail, museums and temples; all 4 interests covered; daily utilisation 0.66–0.69.
  - Budget: known maximum ₹15,555, status INCOMPLETE because entry costs are unknown.
  - The same trip at RELAXED pace has utilisation 0.50–0.54 with 141–241 free minutes per day. A 7-day PACKED plan takes 115 ms.

**Known limits:**
- Travel times are straight-line estimates until Phase 7.
- Stop order within a day is insertion order; Double-Tree ordering comes in Phase 7.
- 3,370 activities have no sourced opening hours. They are scheduled with a warning.
- No itinerary UI yet (Phase 9).

**Gate:**
- Hard budget and time limits are never silently violated (tests: hard budget caps stops, mandatory over-budget → 422, too-long must-visit → 422, impossible booking → 422).
- Unknown cost is not zero (tested).
- Day timelines fit (asserted in planner and API tests).
- Infeasible plans explain why (conflicts, relaxations, unscheduled reasons, uncovered reasons).
- Edits re-plan safely: kept stops stay on their day, new avoids are honoured, and repair reports what it drops.

**Next phase prerequisites (Phase 7):**
- Q2 routing decision (OSRM or labelled estimate).
- Per-day distance matrix from the scheduled stops.
- Double-Tree reordering must keep fixed bookings and meal timing.

## Phase 7 report — Daily route ordering and navigation (passed)

**Checklist:** all 12 items done (see `IMPLEMENTATION.md`).
- **Routing:** self-hosted OSRM v6.0.0 (car, Contraction Hierarchies) via Docker Compose profile `routing` and `scripts/osrm-prepare.sh` (D27).
- **Travel matrix:** one directed `/table` call over base + candidates per plan. Unroutable pairs use the estimate and are counted.
- **Ordering:**
  - Metric validation per day on the symmetrised matrix.
  - Double-Tree closed tour from the base, or an open path with no bound claimed.
  - The order is applied only when it stays feasible with directed legs and is shorter (D28).
  - Fixed bookings, opening hours and meals are re-checked by simulation, so mandatory events keep their times.
  - Nearest-neighbour and the exact optimum are reported next to Double-Tree.
- **Route and navigation:** OSRM `/route` geometry per day. Google Maps URLs with at most 9 waypoints each, chained.
- **Provider failure:** falls back to the labelled straight-line estimate with a warning. The plan is still returned.
- **UI:** new `/plan` page. Interest weights, a base picked by Trie autocomplete, dates, pace and transport. Day tabs show the map route with numbered stops, the timeline, meals, ordering comparison, Google Maps link(s), warnings and infeasibility conflicts.

**Commands and results:**
- `./mvnw -o test spotless:check`: 171 tests, 0 failures, 0 errors.
  - New: `RoutingServiceTest` 5 (OSRM parsing against a local stub server, null pairs, error code, outage fallback, walking), `NavigationLinksTest` 3, five route-ordering tests in `ItineraryPlannerTest`, plus route and ordering assertions in `ItineraryApiTest`.
  - `package` repackaging is blocked while the local server runs (Windows jar lock), as in Phase 6.
- `npm test`: 11 passed (new `PlanPage.test.tsx` 3). `npm run lint`: 0 errors (existing warnings only). `npm run format:check` clean. `npm run build` OK.
- OSRM preprocessing: extract about 4 min, contract about 5 min (16 CPUs). A 121-point table takes 67 ms on CH; on MLD it took 543 ms.
- Live check, OSRM, same demo as Phase 6 (Candolim, 3 days):
  - Plan time 46 ms.
  - Road distances per day: 14.3, 36.5 and 17.0 km.
  - Every day's matrix is metric, so the guarantee applies. Insertion order is within 0.5 min of exact; Double-Tree is 0.5–1.5 min longer, so insertion is kept and the response says so.
  - Open-route day: no guarantee; the Double-Tree order broke opening hours, so the insertion order was kept with that reason.

**Gate:**
- Every stop appears exactly once (asserted).
- A closed tour returns to the base (`legs = stops + 1`, geometry ends at the base).
- The approximation claim appears only for a closed tour on a validated metric matrix (non-metric and open-path tests).
- Real legs are labelled: `travelProvider` OSRM or ESTIMATE, `travelBasis`, route `provider`/`basis`, and outage warnings.

**Known limits:**
- No unit test drives the "Double-Tree adopted" branch with real data, because insertion was always as short. The comparison numbers are reported per day.
- OSRM speeds are free-flow car speeds.
- The plan page is minimal; Phase 9 builds the full itinerary UI.
- No visual browser check: the Chrome extension was not connected. Page served (HTTP 200) and component tests pass.

**Next phase prerequisites (Phase 8):** a road-trip input model, Mumbai-to-Goa routing (the western-zone OSRM graph covers it), and segment and overnight handling.

## Phase 8 report — Road-trip planner (passed)

**Checklist:** all 15 items done (see `IMPLEMENTATION.md`).
- **Corridor data:** Konkan corridor destination built by the existing pipeline: bbox minus Goa, 6,283 places, 830 of them places to stay (D29).
- **Planner and API:** `POST /api/v1/road-trips/plan` (D30).
  - Fixed waypoints keep their order; detours are found in a corridor, ranked by the recommendation engine, bounded and optional.
  - Detours are ordered between fixed ends by Double-Tree open path, with no bound claimed.
  - Driving days follow daily and continuous limits, with rest and lunch breaks.
  - Overnight stops are either user waypoints or suggested OSM lodging.
- **Labels:** fuel CURATED_ESTIMATE; tolls, accommodation and food USER_ENTERED or UNKNOWN; daily Google Maps links.
- **Routing changes:**
  - OSRM `NoRoute` is now reported as unreachable rather than as an outage.
  - The straight-line estimate uses two speeds (D31).
  - `Polygon` uses the outer/inner rule.

**Commands and results:**
- `./mvnw -o test spotless:check`: 192 tests, 0 failures. New: `RoadTripPlannerTest` 14, `RoadTripApiTest` 2, `KonkanCatalogueImportTest` 1, `CorridorBoundaryTest` 1, `TransportModeTest` 2, plus 1 in `RoutingServiceTest`.
- `npm test`: 11 passed (frontend unchanged).
- **Live demo, OSRM:** Mumbai CSMT → Candolim, max 6 h of driving per day.
  - 2 days, with an overnight at OSM-listed "Manjunath" (Sindhudurg).
  - 2 heritage detours, totalling 178 of the 180 allowed minutes.
  - Rests every 120 min of driving, plus lunch. Arrival day 2 at 11:44. Plan time 114 ms.
- **Live demo with user waypoints:** Ratnagiri as an overnight stop, then Malvan with a 60-minute stay. Order and stays were kept, and the costs labelled.

**Bugs found and fixed (each test written first):**
- Waypoint nodes shared between segments overwrote their matrix index, so segment times came out as 0.
- On a backtracking route, places to stay were assigned to one segment only.

**Known limits:**
- The API serves requests while the first-run catalogue import is still in progress, which can briefly give an incomplete corridor.
- No road-trip UI yet (Phase 9).
- Mid-route lodging coverage in OSM is sparse.
- Routed travel times assume free-flowing traffic.

**Gate:**
- The road trip differs from a destination itinerary: segments, driving days and overnights.
- Fixed stops are preserved (zig-zag test).
- Driving and rest limits are respected (asserted on every day).
- Detours are optional and bounded (limits, drop-on-conflict, tight-arrival test).
- Estimates carry confidence labels.

## Phase 9 report — Complete itinerary UI and editing (passed)

**Checklist:** all 13 items done (see `IMPLEMENTATION.md`).
- **Home:** three modes (plan today, multi-day trip, road trip) and demo scenarios: Goa beach weekend, Old Goa heritage day, South Goa family trip, and Mumbai to Goa drive.
- **Preference wizard:** interests with importance; base, dates, days and day window; pace, transport, party size, budget (amount, per person or total, hard or flexible) and food per day.
- **Discovery:** the map and list from Phases 4–5. The nav now links Home, Discover, Plan and Road trip.
- **Itinerary results:**
  - Overview of each day with stacked workload bars.
  - Budget table: every line has a confidence badge, and unknown amounts read "not known", never ₹0.
  - Day timeline: travel to each stop, visit times, wait for opening, meals, return to base, and cost and opening-hours badges.
  - Route map and Google Maps links.
- **Edits:** lock, remove, replace, move to another day, add a must-visit place, and replan keeping locked stops. Each edit re-runs the planner (D32). Backend change: `fillFreeTime` on the itinerary request.
- **Road trip page:** from, to and ordered stops, each with optional overnight and stop minutes. Also departure date and time, arrive-by, driving limit, detour interests and limits, and known costs. Results show the summary, provider, warnings, segments with detours, a timeline per day, the overnight with its badge, the budget and navigation links.
- **States:**
  - Loading status while planning.
  - Empty prompts before the first plan.
  - Error notices with conflicts, suggested relaxations and the estimated arrival.
  - Provider-unavailable notice when road routing falls back to estimates.
  - The map component already falls back when tiles fail.

**Commands and results:**
- `./mvnw -o test spotless:check`: 193 tests, 0 failures. New: `ItineraryPlannerTest.removingAStopWithoutRefillKeepsOnlyTheKeptStops`.
- `npm test`: 23 passed. New: `PlanPage.test` (11), `RoadTripPage.test` (3), `HomePage.test` (1).
- `npx tsc -b`, `npm run build`, `prettier --check`: clean.
- `oxlint`: only warnings that were already there.

**Live check:** backend jar with OSRM, called through the Vite proxy on :5173.
- Itinerary demos:
  - Beach weekend: 4 and 3 stops, budget incomplete.
  - Old Goa: 4 stops.
  - South Goa family: 3, 2 and 3 stops.
- Edits:
  - Remove: the stop is gone and all others are kept on their days.
  - Move: the stop moves from day 1 to day 2.
  - Replace: a new suggestion fills the gap.
- Mumbai to Goa demo: 2 days, overnight at "Manjunath" (SUGGESTED), 2 heritage detours.
- Place search: the Konkan catalogue answers, for example "Hotel Ratna".
- **Browser check not done:** the Chrome extension was not connected in this session. The UI was verified by the component tests and the API calls above.

**Bug found and fixed:** the Days input reset to 1 when cleared, so typing "3" gave "13". The field now stays empty while you type and is clamped to 1..14 when the plan is generated.

**Known limits:**
- Edits are not saved; reloading the page loses them. Saved trips come in Phase 10.
- Road-trip stops cannot be reordered after adding; remove and add again instead.

**Gate:**
- The full flow from your inputs to an itinerary works for all three modes.
- Results explain themselves: provider, labels, coverage gaps, budget and workload.
- Results can be edited.
- The demos need no provider credentials.

## Phase 10 report — Shared trips and collaboration (passed)

**Checklist:** all 12 items done, including shared expenses (see `IMPLEMENTATION.md`).
- **Accounts:** username + password, BCrypt, session cookie (HttpOnly, SameSite=Lax). Pages to sign in or create an account, plus sign out (D34).
- **Trips and roles:** owner, editor and viewer roles, checked on the backend on every call. Non-members get 404 (D35).
- **Read-only link:** 256-bit token, stored hashed. It can expire and be revoked. The public `/shared/{token}` page leaves out the request and members, and is rate-limited (60 per minute per IP).
- **Members:** the owner adds a member by username with a role, changes roles and removes members.
- **Suggestions and votes:** one vote per person; the same vote again returns 409, the opposite vote changes it.
- **Change history:** who did what, at which trip version.
- **Optimistic locking:** every edit sends its version. A stale edit returns 409 `VERSION_CONFLICT` with the current version; the UI loads the latest trip and says why.
- **Shared expenses:** an equal split with a balance per person.
- **Frontend:**
  - "Save trip" on the itinerary and road-trip results.
  - My trips page.
  - Trip page: edits go through `/trips/{id}/generate` with the version they were based on.
    - Owner controls: rename, delete, people, share links.
    - Suggestions with voting and "add to plan", expenses, history.
    - Polls every 15 s.
  - Public shared page.
  - Itinerary edit logic moved to `plan/edits.ts`, shared by the plan page and the trip page.

**Commands and results:**
- `./mvnw -o test spotless:check`: 206 tests, 0 failures. New:
  - `AuthApiTest` (4): register, login, me and logout; BCrypt; the same error for unknown and wrong; validation; 401 without a session.
  - `TripCollaborationApiTest` (9):
    - Owner, editor, viewer and outsider permissions.
    - Member rules.
    - Share link is read-only, unguessable, revocable and expires.
    - Stale edit returns a conflict.
    - Suggestions and the duplicate-vote rule.
    - History order.
    - Expense split.
    - Generate with a version check.
    - Rate limit.
- `npm test`: 34 passed. New:
  - `TripPage.test` (6): owner share and revoke; viewer read-only; editor edit with version; conflict reload; votes and expenses; shared page and dead link.
  - `LoginPage.test` (3).
  - `PlanPage.test`: save trip and the sign-in prompt (2).
- `tsc -b`, `npm run build`, `prettier --check`: clean.

**Live check:** real PostgreSQL (V4 migrated), OSRM, through the Vite proxy on :5173.

| Check | Result |
|---|---|
| Register | 201, `JSESSIONID` HttpOnly, SameSite=Lax |
| Wrong password | 401 |
| `/trips` with no session | 401 |
| Owner generates a 2-day plan | v1, 5 and 5 stops, OSRM |
| Viewer GET / PATCH | 200 / 403 |
| Editor renames / creates a share link | 403 / 403 |
| Editor removes a stop | v2, 10 → 9 stops |
| Owner sends a stale v1 | 409 `VERSION_CONFLICT`, current version 2 |
| Shared link without login | 200, no request or members |
| Shared link after revoke | 404 |
| Vote / same vote / opposite vote | 201 / 409 / 200 |
| ₹2,700 split three ways | +1800 / −900 / −900 |
| History | Full sequence |
| Logout | Logout 204, then `/me` 401 |

**Browser check not done:** the Chrome extension was still not connected. The UI was verified by the component tests and the API checks above.

**Known limits:**
- Each IP gets its own in-memory rate-limit window, so the limit applies per backend instance.
- No email invitations; members are added by username.
- Updates arrive by polling, not WebSockets.
- Deleting a trip archives it; there is no restore screen.
