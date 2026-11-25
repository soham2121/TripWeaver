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
| 11 — No-Google-dependency verification | Passed |
| 12 — Benchmarks and audit | In progress (benchmarks done; complexity and licensing review open) |
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
- **Optional credentials:** none. (Originally `GOOGLE_PLACES_API_KEY`; Google Places dropped on 2026-11-24, D39.)
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

## Browser check of Phases 9–10 (2026-11-23, Chrome, real backend + OSRM + PostgreSQL)

Clicked through the app in Chrome. Working end to end:
- Home page and the Goa beach weekend demo.
- Itinerary edits: remove, with times recomputed.
- Account creation, then saving the trip.
- Trip page: owner panels, local times, expense added, edit with a version bump.
- Read-only link: opens in a second tab, then fails after revoke.
- Mumbai to Goa road-trip demo: route on the map, detours and the day timeline.

Bugs found and fixed:
- **Map stayed blank everywhere.** Present since Phase 4; the component tests mock the map, so they never caught it.
  - Cause: MapLibre's web worker failed to load ("Worker failed to load"). Vite pre-bundles the library in dev and does not emit the worker in production builds.
  - Fix: `setWorkerUrl` with the `?url` import of `maplibre-gl-worker.mjs`. The build now emits the worker, and tiles, routes and markers draw.
- **Times shown in UTC.** Trip, shared and trip-list times were raw UTC. They now use the viewer's local time (`localTime`).
- **Budget table overflowed** in the narrower trip-page column. The overview and budget now stack at full width.
- **Revoked link stayed visible.** A revoked link remained in the "copy this link" box; it is now cleared (test added).
- **Trip titles said "(default)".** The default base name was "Candolim (default)". It is now "Candolim", and the hint is shown only next to the base.
- **Plans could start in the past.** Plans without a start date began today even in the evening. After 17:00 they now start tomorrow.
- **Expense amounts in history** showed "₹1200.0". They now show "₹1200" (test added).

Found, not fixed (needs a decision): nightlife places with no sourced opening hours can be scheduled in the morning (Viva Pub at 09:04 in the beach-weekend demo).

## Open items (noted 2026-11-23; all closed by 2026-11-24)

Closed: the multi-day crash (commit e3bfdb6), road-trip town stays (e3bfdb6), nightlife hours (D44) and the browser re-test (see "Browser re-test, dark mode and Phase 12–13" below).

- **Multi-day itinerary returns a server error.** `ItineraryPlanner.time()` calls `LocalTime.of` with hour 24 when a simulated time reaches midnight (path: `fillOptional` → `simulate`). Seen in the backend log as `DateTimeException: Invalid value for HourOfDay (valid values 0 - 23): 24`. Not fixed yet.
- **Road trip updates.** Requested by the user; details to be agreed.
- **Nightlife hours.** Places with no sourced hours can be scheduled in the morning (Viva Pub at 09:04). Proposed fix: evening-only hours, labelled as an estimate. Waiting on a decision.
- **Re-test the app in the browser** after these fixes.

## Supabase database and Google Places provider (2026-11-24)

**Phase A — Supabase inspection and connection (passed, read-only).**
- Project `TripWeaver` (ref `wtslfglvhmspwmjdwcfw`, region ap-southeast-2, PostgreSQL 17.12), reached through the Supabase CLI (`supabase link`, `supabase db query --linked`, Management API). No Supabase MCP server was available in this session.
- Found: `public` empty; PostGIS 3.3.7 already in `extensions`; advisors clean; role `postgres` can create schemas.
- The direct host is IPv6-only and unreachable from the dev network. The Session Pooler (`aws-1-ap-southeast-2.pooler.supabase.com:5432`) is reachable.
- `application.yml` now reads `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (fallback: local DB) and an optional `.env`. Hikari pool size 5.

**Phase B — Migrations (passed locally; Supabase waits for the password).**
- `V5__catalogue_constraints_and_external_refs.sql` (portable): confidence CHECKs; coordinate, duration and cost CHECKs; 10 missing foreign-key indexes; `external_place_references`.
- `db/vendor/postgresql/V6__postgis_and_rls.sql`: PostGIS in `extensions`; generated `places.geog geography(Point,4326)` with a GiST index; RLS on every table with no policies; grants revoked from `anon`/`authenticated`.
- `afterMigrate.sql` does the same for `flyway_schema_history`. Flyway holds a lock on that table during migrations, and altering it inside V6 deadlocked on the first try.
- The local docker image is now `postgis/postgis:17-3.5-alpine` (same volume, data kept).
- `scripts/verify-db.sql`: 8/8 checks true on the local database (11,475 places, 33 MB).

**Phase C/D — Catalogue and OSM ingestion.** Already in place from Phase 3: Java `OsmExtractTool`, boundary filter, idempotent upsert by OSM id and an import report. Added live coverage by region × category to `GET /api/v1/destinations/{slug}/coverage` (`byRegionAndCategory`, counted from the database).

**Phase E — Curated metadata.** The format is unchanged: `data/curated/<slug>-curated.json`, with source URL, confidence and verifiedAt per field. No new curated facts were added in this round; unknowns stay unknown.

**Phase F — Backend integration.** `SpatialPlaces`: `/places/nearby` uses `ST_DWithin` on the GiST index when PostGIS is present (EXPLAIN shows an index scan). Without PostGIS (H2 tests) it uses the in-memory index. Ranking and all assessed algorithms are unchanged.

**Phase G — Google Places (optional).**
- `external/GooglePlacesClient`: Places API (New), field masks, key only in a header, 4 s timeout.
- `PlaceEnrichmentService`: feature flag, daily limit, place-ID-only storage, 429 and 502 errors.
- `GET /api/v1/places/{id}/enrichment`, rate-limited per IP.
- Frontend `GoogleDetails`: loads only on click; shows the "Google Maps" attribution, the review author's name, photo and profile link, and the review link; not drawn on the map.

**Phase H — Checks run.**
- Backend `./mvnw test`: 220 run, 0 failures, including 9 new enrichment tests against a local stub server. `spotless:check` clean.
- Frontend `vitest run`: 37 passed (3 new). `tsc -b` clean. `oxlint`: 3 warnings, all there before this work.

**Supabase run (same day).** The Sydney project was empty and was deleted at the owner's request. It was replaced by `TripWeaver` in ap-south-1 (Mumbai), ref `ddwdxesatvvlaxungxxb`, created with the CLI; its password was generated locally and written only to `.env`. The backend migrated V1–V6 through the Session Pooler (`aws-1-ap-south-1.pooler.supabase.com`) and imported goa (5,192) and konkan (6,283). `verify-db.sql`: 8/8 true, 35 MB. Advisors: no WARN or ERROR; INFO only (`rls_enabled_no_policy`, intended per D37; `unused_index` on a new database). `/places/nearby` answered from Supabase PostGIS.

**Superseded the same day:** the Google Places provider (Phase G) was removed when Google Places went out of scope (D39). It was never committed or used with a real key.

## Phase 11 report — No-Google-dependency verification (passed, 2026-11-24)

The phase replaced "Optional external enrichment" after Google Places went out of scope (D39).

| Check | Evidence |
|---|---|
| No Google API in code | `NoGoogleDependencyTest` (backend, 3 tests): no Google API host, SDK, key setting or header in `src/main` outside migration history; no Google dependency in `pom.xml`; the only `google.com/` reference is `NavigationLinks`. `src/test/noGoogle.test.ts` (frontend, 2 tests): same for `src` and `package.json` |
| No Google content stored | `scripts/verify-db.sql` check "no Google content stored (D39)": true locally and on Supabase |
| Runs without a key | No Google setting exists in `application.yml` or `.env.example`. The backend started against Supabase and planned a 2-day Goa trip; the response has no Google text besides the directions links |
| Discovery uses the catalogue | Search, autocomplete, nearby (PostGIS) and details read only TripWeaver tables |
| Map | Chrome check: `/plan?demo=goa-beach-weekend` drew OpenFreeMap tiles, the OSRM route and numbered stops, with "© OpenStreetMap" attribution. The page made network requests to `localhost` and `tiles.openfreemap.org` only. One OpenFreeMap font file returned 404 (cosmetic; it existed before). Style URL configurable: `VITE_MAP_STYLE_URL` |
| Routing | Live: `travelProvider = OSRM`. Off: `ItineraryApiTest` and the other API tests run with `osrm-url: ""` and get labelled estimates plus the "Road routing unavailable" notice (D40) |
| Navigation | `NavigationLinksTest` (3): `api=1` URLs without credentials, split at the waypoint limit |
| Core planning with all external services off | All 216 backend tests run with no OSRM, no network and in-memory H2 |

## Phase 12 progress — Benchmarks (2026-11-24)

- `AlgorithmBenchmarks`: Quickselect vs sort (1K–1M); Set Cover lazy heap vs full greedy (20–500 candidates × U 20/60), plus greedy vs exact and vs cheapest-first; heap vs linear scan (1K–500K ops); Double-Tree vs Nearest-Neighbour vs Held–Karp (10–200 stops), with the metric check timed separately.
- Each pair is checked for identical output (or the 2× bound) before timing. Raw CSVs are in `benchmark-results/raw/`, and charts in `docs/benchmarks/*.svg` come from `scripts/benchmark/plot.mjs`.
- Honest findings, written up in `BENCHMARKS.md`:
  - Lazy greedy does 3–11× fewer gain evaluations but is slower in wall-clock time at small universes.
  - The heap loses to a scan at 1K operations.
  - Nearest-Neighbour gives shorter tours than Double-Tree on random points, but has no guarantee.
  - Double-Tree time is dominated by its O(n³) metric check.
- **Still open for Phase 12:** check that complexity notes match the code; review the licensing and provider documentation once more.

## Planner fixes (2026-11-24)

- **Late lunch:** a food stop opening in the evening, or a run of food stops, could push the lunch break to the next non-food stop (seen at 18:27). Breaks now fall due when their window opens, or up to 30 min early if the next stop would overrun the window. A food stop counts as the meal only if its projected start (after opening hours) falls inside the window. A break more than 60 min past its window is dropped and never placed late. Test: `lunchIsTakenInItsWindowEvenWhenTheNextStopIsALateOpeningRestaurant` (failed with lunch at 19:15 before the fix). Live: six seeds of a 2-day Goa nightlife plan put lunch at 13:15–14:08.
- **Club hours:** bars and clubs whose hours cannot be parsed now get the labelled 20:00–03:00 estimate, as bars with no hours already did. Before, they were treated as open all day. Test: `clubWithUnparseableHoursGetsTheEveningEstimateNotAnAfternoonSlot` (failed at 19:06 before).
- **Build target:** Java 21 (D42). Backend: 216 tests pass. Frontend: 36 tests pass; `tsc`, `prettier` and the production build are clean.

## Owner answers (2026-11-24)

- **Q1 → D45:** solo project with the teacher's permission. `docs/CONTRIBUTIONS.md` written.
- **Q7 → D46:** `V7__drop_google_leftovers.sql` applied. Both objects were checked empty first. Applied on the local database (via a one-off run) and on Supabase (on the backend restart). `verify-db.sql`: 8/8 true on both, Flyway at V7. Backend tests: 216/216.
- **UI refresh (D47):** new colour system, fonts, route-rail timelines, skeleton loading, View Transitions; mobile overflow fixed on plan, road trip and discover. Checked in Chrome at desktop and 390 px. Frontend 36/36 tests, `tsc` and `prettier` clean, production build ok, impeccable detector clean.

## Browser re-test, dark mode and Phase 12–13 (2026-11-24, evening)

**Dark mode (D48).** Toggle in the header; the system setting is the default and a choice is saved. Pre-paint script, so no flash. Maps switch to OpenFreeMap `dark`. Test: `theme toggle switches to dark and remembers it`.

**Browser re-test** (Chrome, backend on Supabase, OSRM up). These flows passed:
- Home and every demo.
- Plan today, with the Trie base picker (`panj` → Panaji).
- Multi-day, 2 and 4 days.
- Road trip, Mumbai to Goa: 568 km, 1 night, OSRM.
- Discover.
- Sign-up and sign-in.
- Saving a trip.
- Adding an expense (₹850 split).
- Creating a read-only link, opening it in a second tab, and revoking it (the link then shows "invalid, revoked or expired").
- Light and dark themes.

No console errors.

Bugs found and fixed:
- **Signing in to save lost the plan.** "Sign in to save this trip" went to `/login`, which unmounted the planner, so the user came back to an empty form. Sign-in is now inline on the same page (`LoginPage inline`), and the plan stays. Test: `SaveTrip.test.tsx`.
- **"Known total ₹0"** when every cost was unknown. It now says "No costs known yet". Test: `BudgetTable.test.tsx`.
- **Per-person limit mislabelled.** The limit read "hard limit ₹27,000 per person", but the backend's `limit` is the group total (budget × party). It now reads "₹27,000 for the group (₹9,000 per person)". Test added.
- **Weekday labels** were lower case ("wednesday"). They are now capitalised.

**Phase 12 closed.**
- Licences rechecked (`DATA_SOURCES.md` §9).
- Complexity notes spot-checked against the code: TreeMap children in the Trie; HashMap positions and Floyd heapify in the heap; O(n²) Prim; three-way partition in Quickselect.

**Phase 13.**
- New documents:
  - `docs/PROPOSAL.md`;
  - `docs/REPORT.md`, which includes the architecture diagram, assumptions, limitations and failure cases;
  - `docs/DEMO_SCRIPT.md`, the 8-minute script with viva notes.
- New demo "Four days in Goa with friends" (`goa-four-day-friends`, the §18 scenario).
- Screenshots in `docs/screenshots/`.
- **Clean-clone check:** a fresh `git clone` of the committed `dev` branch passed:
  - `./mvnw verify` (216 tests, 0 failures);
  - `npm ci`;
  - `vitest` (36 tests at that commit);
  - `npm run build`.
- **Checks now:** frontend 40/40 tests; `tsc` and `prettier` clean.

**Left for the owner:**
- Rehearse the demo once.
- Commit, then tag the final version.
- Viva practice.

## Hosting: Vercel + Render (2026-11-24, night)

- **Frontend:** Vercel project `trip-weaver` builds `frontend/` from `dev` (https://trip-weaver-tau.vercel.app).
  - `frontend/vercel.json` forwards `/api/*` to Render.
  - Every other path is served `index.html`, so deep links work.
- **API:** Render Docker web service `tripweaver-jo2x` (Singapore, free plan) built from `backend/Dockerfile`, using the Supabase database.
  - Startup on the free instance takes about 90 s.
  - A build filter limits rebuilds to backend and data changes.
- **Live checks through the Vercel URL, all passing:**
  - health;
  - Trie autocomplete;
  - town search;
  - a 4-day plan with a hard budget (2.4 s warm);
  - the Mumbai–Goa road trip (labelled estimates, no OSRM);
  - an invalid share link (404);
  - a wrong login (401).
- **Checked live on 2026-11-25:** sign-in (owner's test account) without losing the plan, saving a road trip, and a read-only link (opened in a second tab, `GET /api/v1/shared/{token}` 200 with no cookie, then revoked: 404 `SHARE_LINK_INVALID`, and the page says "This link is invalid, revoked or expired"). No console errors.
- **Map fallback seen:** "Map tiles are unavailable" appeared because a browser content blocker cancelled `tiles.openfreemap.org/planet` (`ERR_BLOCKED_BY_CLIENT`). It is not an app bug.
- **Cleanup done (4f04f7f):** `UserDetailsServiceAutoConfiguration` excluded, so Spring no longer logs a generated password.
