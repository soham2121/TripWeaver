# TripWeaver — Complete Implementation Plan

> **Product:** Personalised trip planning, itinerary optimisation and collaborative travel
> **CIA:** Advanced Algorithm, Semester V, 2026–27
> **Stack:** Java 21 (builds and runs on JDK 21 or newer) + Spring Boot, React + TypeScript, PostgreSQL
> **Initial destination:** Goa, with an extensible India-wide data model
> **Purpose:** Authoritative implementation specification for Claude Code

# 0. Instructions to Claude Code

Read this entire file before substantial implementation. Treat it as the source of truth for product scope, algorithms, architecture, milestones and acceptance criteria.

Build a working product whose central workflow is:

**User preferences and constraints → place discovery → candidate selection → experience coverage → day allocation → route ordering → time/budget validation → editable itinerary → navigation and sharing.**

## 0.1 Mandatory engineering rules

1. Implement in the phases in Section 16. Do not skip acceptance gates.
2. All assessed algorithms and itinerary-planning business logic execute in Java on the backend.
3. Do not replace assessed algorithms with JavaScript packages, Google Maps, OSRM or another external optimiser.
4. External services may provide source data, maps, road distances, route geometry, weather or place enrichment. TripWeaver must perform its own candidate selection, experience coverage, day allocation and stop ordering.
5. Do not invent place names, ratings, review text, opening hours, ticket prices, costs or route times.
6. Label data as verified, provider-sourced, curated estimate, user-entered, synthetic/demo or unknown.
7. Respect OpenStreetMap licensing and attribution (ODbL). **Google Places API is out of scope (D39):** no Google Cloud project, billing, API key, SDK, HTTP client or API call, and no Google ratings, reviews, photos or other Places content. Never scrape Google Maps or Google search. Plain Google Maps directions URLs for navigation handoff are allowed; they call no API.
8. Never expose API keys in frontend code, source control, logs or error responses.
9. Do not claim global optimality for heuristic or approximation algorithms.
10. Write tests before integrating each algorithm. Run relevant tests after every meaningful change.
11. Never fabricate test results, benchmark numbers, API responses or completion status.
12. Keep the application runnable locally and independent of optional paid APIs for its core workflow.
13. Avoid microservices and unnecessary infrastructure. Use a modular monolith.
14. Keep `docs/PROGRESS.md`, `docs/OPEN_QUESTIONS.md`, `docs/DECISIONS.md` and this checklist updated.
15. Do not add features that do not serve the product workflow or assignment just to inflate scope.

## 0.2 Phase execution protocol

At the start of each phase:
- Read the phase scope, checklist, dependencies and acceptance gate.
- Inspect the existing repository and progress report.
- Identify blockers and make a task breakdown.
- Implement in small, reviewable steps.
- Run tests, build and lint relevant to the change.
- Inspect the diff for secrets, data-policy violations, fake data and unrelated scope changes.

At the end of each phase, report:
- Phase name and status.
- Checklist items completed and incomplete.
- Files created or modified.
- Commands actually run.
- Test counts and exact pass/fail counts.
- Build/lint status.
- Known defects and unresolved questions.
- Whether the acceptance gate passed.
- Next phase prerequisites.

**Stop at the phase gate.** Do not silently proceed if it fails. Fix the failure or document a blocker. If asked to continue through phases, proceed only when dependencies and gates pass; preserve checkpoints in the repository.

---

# 1. Product definition

TripWeaver is a personalised travel-planning platform. A user tells the app where they are going, what they want to experience, how much time and money they have, where they are staying, how they will travel, and how relaxed or packed they want the trip to be.

TripWeaver discovers places, selects a diverse set of experiences, distributes activities across days, orders visits geographically, checks time and budget feasibility, and creates an editable itinerary. Trips can be private, shared by link or collaboratively edited.

## 1.1 Three primary planning modes

### A. Plan today

Example: “I am in Panaji, have six hours, want local food, heritage and a relaxed walk, and want to spend no more than ₹1,500.”

Inputs: start location, time window, interests and weights, budget, transport, pace, accessibility needs, must-visit/avoid places and return location.

Output: relevant shortlist, route order, visit/travel times, estimated spend and confidence, timeline, map and navigation link.

### B. Multi-day destination trip

Example: “Four days in Goa, staying near Candolim. I want beaches, Goan restaurants, forts, water activities and one nightlife evening. One packed day and the rest balanced or relaxed. ₹25,000 per person excluding travel to Goa.”

Inputs: destination, dates, accommodation/base, check-in/out times, party size, interests and weights, trip pace, per-day pace overrides, total/per-person budget, inclusions/exclusions, transport, fixed bookings, must-visits and avoid list.

Output: day-wise itinerary, geographically sensible grouping, activity and travel durations, meal/rest buffers, daily and total spend estimates, workload/pace indicator, map, navigation links and editable stops.

### C. Road trip

Example: “Leave Mumbai today, reach Goa within two days, stay near Candolim/Calangute/Baga. Scenic but not exhausting drive, an overnight stop and a few worthwhile detours.”

Inputs: origin, destination, departure, latest arrival, vehicle/mileage/fuel assumptions, maximum driving hours per day, fixed/overnight waypoints, detour limit, food/rest preferences, budget and destination stay area.

Output: main route, driving estimates, rest/meal stops, detours and their extra travel cost, overnight stop, fuel/toll estimates with confidence labels, arrival plan, destination itinerary and separate navigation links per leg.

### D. Shared trips

Owner, editor and viewer roles. Minimum MVP: a secure read-only shared link and revocation. Later collaboration: invitations, suggestions, voting, shared expenses, change history and concurrent-edit conflict handling.

## 1.2 Product non-goals for the first release

- Payments, hotel/restaurant reservations or ticket booking.
- Scraping review sites or Google Maps.
- Claims of live availability without a verified integration.
- Guaranteed traffic or exact arrival time.
- Nationwide coverage before the Goa workflow is strong.
- An AI chatbot replacing the planning engine.

## 1.3 Initial destination scope

Create a high-quality Goa catalogue covering multiple regions, not only Baga/Candolim. Include North Goa, Panaji/Old Goa, central and South Goa, plus selected inland/nature attractions where access and seasonal information can be verified. Show catalogue coverage gaps. New destinations should be added through data ingestion and curation, not algorithm rewrites.

---

# 2. CIA 3 compliance and algorithm selection

The supplied CIA guideline requires:
- A group of 2–3.
- Java backend and JS-family frontend.
- At least two meaningful syllabus techniques, with at least one from Units 4–6.
- CO5 through a syllabus-covered randomized algorithm or probabilistic structure.
- Original Java implementations of assessed algorithms.
- Real or realistic synthetic data, with assumptions and failure cases.
- At least five documented correctness tests per core algorithm, including an edge/boundary case.
- Benchmarks at at least three increasing sizes.
- Baseline comparison and reproducible setup.

## 2.1 Recommended assessed algorithms

| Algorithm | Syllabus unit | Actual product responsibility |
|---|---|---|
| Trie | Unit 4 — Text search | Place, activity, cuisine and destination autocomplete |
| Randomized Quickselect | Unit 1 / Unit 6 — Selection/randomization | Select top candidate places by relevance score; CO5 |
| Greedy Weighted Set Cover Approximation | Unit 5 — Set cover | Select a varied set of destinations that collectively cover requested experiences |
| Binary Max Heap / Indexed Heap | Unit 2 — Binary heaps | Efficiently maintain changing marginal priorities during lazy greedy selection |
| Metric TSP Double-Tree Approximation | Unit 5 — Metric TSP approximation | Order daily sightseeing stops under validated metric-distance assumptions |

This is intentionally broader than the minimum. Each technique has a real job. Do not add more algorithms simply to increase the count.

## 2.2 Supporting procedures (not automatically counted as syllabus algorithms)

- Greedy day-allocation/insertion heuristic.
- Constraint validation and repair.
- Road routing and distance-matrix acquisition.
- Exact brute-force solvers for small test instances only.
- Shared-trip version checks and conflict handling.

A routing provider may return road legs or geometry; it must not decide TripWeaver's assessed stop order.

## 2.3 CIA compliance checklist

- [x] Team has 2–3 students. Exception: solo project with the teacher's permission (D45).
- [ ] Backend is Java; frontend calls its APIs.
- [ ] Five proposed core algorithms are implemented in Java.
- [ ] At least two distinct techniques are syllabus-approved.
- [ ] Unit 4–6 technique included.
- [ ] Randomized Quickselect genuinely demonstrates CO5.
- [ ] At least five documented correctness tests per core algorithm; target 8–12.
- [x] At least three increasing benchmark sizes per core algorithm.
- [ ] Baseline comparisons documented; target a comparison for each core algorithm.
- [ ] Time/space complexity matches actual code.
- [ ] Real/synthetic data provenance and failure cases documented.
- [ ] Clean local setup and representative sample data included.
- [ ] Proposal, technical report, README, demo and contribution record complete.
- [ ] Every member can explain an algorithm and their own contribution.

---

# 3. Input model and planning constraints

## 3.1 Trip-level inputs

- Destination/region and travel dates.
- Party size and traveller types.
- Budget amount and currency (INR initially).
- Budget basis: per person or total group.
- Budget strictness: hard limit or flexible preference.
- Transport mode.
- Accommodation/base location(s).
- Trip-level pace.
- Interests and relative weights.
- Accessibility needs.
- Must-visit and must-avoid places.

## 3.2 Day-level inputs

- Start/end time.
- Per-day pace override.
- Fixed bookings/time windows.
- Meals and rest buffers.
- Daily budget preference (optional).
- Start/end location and whether the day returns to accommodation.
- Mandatory activities.

## 3.3 Pace must be workload-based

Do not define pace only as a number of stops.

Each day workload includes:
- Activity duration.
- Travel duration.
- Meals.
- Rest and transition buffers.
- Opening-hour constraints.
- Activity intensity (e.g. trek versus viewpoint).
- Number of geographical transitions.

Initial labels:
- Relaxed: lower utilisation, larger buffers and fewer major activities.
- Balanced: moderate utilisation and practical variety.
- Packed: higher utilisation with realistic minimum buffers.

Users can override each day. The system must still reject physically impossible schedules. Persist the actual workload metrics used to derive the label.

## 3.4 Budget model

Track accommodation, intercity travel, local transport/fuel, tolls/parking, food, entry fees, activities and miscellaneous buffer.

Every cost stores:
- Minimum/maximum estimate where available.
- Currency.
- Basis: per person, vehicle, room, group, item.
- Source URL/provider.
- Verification date.
- Confidence: verified, provider-sourced, curated estimate, user-entered or unknown.

Unknown cost is never zero. For hard budgets, missing costs must be surfaced as incomplete estimates or requested from the user.

Fuel formula:
`fuelCost = distanceKm / mileageKmPerLitre * fuelPricePerLitre`

Fuel and toll amounts need a source, a user-entered value or a dated, explicitly labelled assumption.

---

# 4. Place data strategy

## 4.1 Source hierarchy

### A. OpenStreetMap — primary geographic catalogue

Use OSM for place names, coordinates, categories/tags, roads, paths, beaches, parks, viewpoints, trails, restaurants, cafes, hotels and attractions where mapped.

Preferred workflow:
1. Download a regional PBF extract from Geofabrik.
2. Parse/import supported features.
3. Normalize tags and categories.
4. Store OSM identifiers and source metadata.
5. Curate important missing fields independently.

Do not rely on public Overpass or public tile servers for heavy production use. Respect usage policies.

References:
- https://www.openstreetmap.org/
- https://www.openstreetmap.org/copyright
- https://download.geofabrik.de/asia/india.html
- https://wiki.openstreetmap.org/wiki/Overpass_API

Follow ODbL obligations, visible attribution and relevant share-alike requirements. Keep OSM-derived records distinguishable from independently curated fields.

### B. Google Places API — out of scope (decision D39, 2026-11-24)

TripWeaver does not use the Google Places API. No Google Cloud project, billing account or API key is required, and none may be added. No Google SDK, HTTP client or API call; no feature flag or dormant integration. Google ratings, reviews, photos and other Places content are never fetched, shown or stored. Google Maps and Google search are never scraped.

Fields that Google would have supplied (opening hours, prices, descriptions) come from OSM tags where present and from independently curated official sources (C below); everything else stays visibly unknown. Navigation handoff uses ordinary Google Maps directions URLs (Section 7, Phase 7), which need no key and return nothing to TripWeaver.

### C. Official sources and curated records

Use official tourism/venue/operator/forest authority/attraction websites for descriptions, opening hours, entry prices, seasonal restrictions and activity information. Store source URL and verification date for each field.

A destination overview page does not prove an exact current ticket price. Only record what the source actually supports.

### D. Initial Goa catalogue

Suggested curation targets (targets, not claims about current source coverage):
- 80–100 restaurants/cafes.
- 50–70 beaches/viewpoints.
- 40–60 heritage/cultural/sightseeing places.
- 30–50 nature/adventure/activity places.

Quality and provenance are more important than count. If fewer records can be verified, use fewer and disclose catalogue coverage.

### E. Weather

Weather is optional enrichment for early releases. Open-Meteo or another permitted API may be used subject to current licence, attribution and commercial-use terms. Store provider and observation/forecast timestamp.

## 4.2 Internal Place model

Suggested fields:
- `id` internal UUID.
- `osmId` nullable OSM element identifier.
- `name`, `description`, `latitude`, `longitude`.
- `destinationId`, `regionId`, `neighbourhood`.
- `primaryCategory`, `categories[]`, `interestTags[]`.
- `estimatedVisitMinutesMin`, `estimatedVisitMinutesMax`.
- `activityIntensity`, `bestTimeOfDay`, `seasonalNotes`.
- `openingHours` only when sourced and timestamped.
- `estimatedCostMin`, `estimatedCostMax`, `costBasis`, `costCurrency`.
- `costConfidence`, `costSourceUrl`, `costVerifiedAt`.
- `accessibilityTags[]`.
- `dataQualityStatus`, `isActive`, timestamps.

No Google Places content is fetched or stored (D39).

## 4.3 Ingestion pipeline

1. Download a documented regional OSM extract.
2. Parse supported OSM objects.
3. Normalize tags/categories.
4. Convert features into internal Place records.
5. Deduplicate stable OSM IDs; use careful name/coordinate matching for independent sources.
6. Import curated venue/attraction fields.
7. Validate coordinates and destination boundary.
8. Record source URL and verification dates.
9. Flag missing duration, cost, opening hours and category.
10. Produce accepted/rejected/merged/unresolved import report.
11. Keep raw extracts out of Git unless licence and repository policy permit.
12. Make the process reproducible.

Incomplete records need not be deleted, but the UI must show what is missing.

---

# 5. Detailed algorithm specifications

All assessed algorithms belong in plain Java classes under `backend/src/main/java/.../algorithms`. They must not depend on Spring, database entities or HTTP DTOs.

## 5.1 Trie — autocomplete

**Unit 4.** Prefix search over place names, activity types, cuisines and neighbourhoods.

Operations:
- `insert(term, reference)`
- `searchPrefix(prefix, limit)`
- `delete(term, reference)` if required
- `containsExact(term)`

Normalize whitespace, case and Unicode consistently, while preserving display spelling separately. Support duplicate terms mapping to multiple places. Do not assume ASCII-only content.

Complexity: O(m) to traverse a prefix of length m, plus result enumeration. Space proportional to indexed characters/references.

Tests: empty trie; exact term; shared prefixes; no match; case normalization; duplicates; Unicode; result limit; delete/reinsert; destination/category filters.

Baseline: linear scan. Benchmark 1K, 10K, 100K and a larger feasible catalogue.

## 5.2 Randomized Quickselect — candidate pruning and CO5

**Unit 1 / Unit 6.**

Each candidate gets a deterministic TripWeaver relevance score from TripWeaver-owned data:
- Interest match and user category weights.
- Distance/detour suitability.
- Budget fit.
- Duration fit.
- Data confidence.
- Seasonal/opening suitability where verified.

Must-visit and must-avoid rules are hard filters, not small score adjustments.

Implement random pivot selection and a three-way partition for duplicate scores. Inject a seedable random source. Return selected candidates, threshold, tie policy and seed metadata. Do not mutate caller input unless explicitly documented.

**Category safeguard:** A single global top-K can eliminate rare interests. Implement category-aware selection quotas or reserve suitable candidates for underrepresented requested categories before merging the selected pool.

Complexity: expected O(n), worst-case O(n²), O(1) partition space excluding a defensive input copy.

Tests: single item; K=0; K=N; invalid K; empty input; ties; sorted/reverse input; multiple seeds versus sorting; input immutability; category safeguard.

Baseline: full sort then select K. Benchmark 1K, 10K, 100K and 1M candidates where feasible.

## 5.3 Greedy Weighted Set Cover — experience diversity

**Unit 5.**

Define:
- Universe `U`: desired experience tags (beach, food, heritage, nature, adventure, nightlife, etc.).
- Each place `p` covers subset `S_p`.
- Each tag `u` has user weight `w_u`.
- Each place has positive scalar selection cost `c_p`.

Selection cost is a documented normalized combination of duration, estimated spending and detour burden, adjusted by user preferences. Keep actual hard time and money limits separate.

At each iteration select the candidate maximizing:

`sum(weight of currently uncovered tags covered by p) / selectionCost(p)`

Stop when target coverage is reached, no positive-gain candidate remains, or an explicit selection limit is reached.

Return selected place IDs, coverage/uncovered tags, selection sequence, marginal gain/cost, and reasons.

**Guarantee boundary:** This is a greedy weighted Set Cover heuristic. Classical weighted Set Cover guarantees apply only to the defined set-cover objective and assumptions. They do not automatically make the complete multi-day budgeted itinerary globally optimal. Do not claim that.

Tests: one tag; multi-tag set; overlaps; ties; empty universe; uncoverable interest; invalid costs; duplicates; zero gain; hard constraint conflict; deterministic tie; exact brute force on small sets.

Baselines: relevance-only ranking, unweighted greedy, exhaustive optimal set cover for small cases.

Benchmark 20/50/100/250/500 candidate places with different universe sizes and overlaps.

## 5.4 Binary Max Heap / Indexed Heap — lazy greedy priority

**Unit 2.**

Implement array-backed max heap. Do not use Java `PriorityQueue` as the assessed implementation.

Operations:
- `insert(entry)`
- `peekMax()`
- `extractMax()`
- `updatePriority(id, priority)` where indexed heap is used
- `contains(id)`, `size()`, `isEmpty()`

Integrate a lazy greedy selection:
- Stored priority is an upper bound on current marginal gain/cost.
- Pop the apparent best candidate and recompute its current gain.
- If it remains at least as good as the next stored upper bound, accept it.
- Otherwise reinsert with updated priority.
- Marginal gain cannot increase as more interests become covered, so an old score remains an upper bound when selection cost is fixed and positive.

Verify that heap/lazy-greedy selection sequence equals a full-recomputation greedy reference under identical tie rules.

Complexity: insert/extract/update O(log n), peek O(1), bottom-up build O(n).

Tests: empty; one entry; ascending/descending inserts; ties; extraction order; update up/down; missing update; random operation sequence versus reference; invariant after every operation; lazy greedy equals full greedy; deterministic tie-breaking.

Baseline: repeated sorting/full priority recomputation.

## 5.5 Metric TSP Double-Tree — daily stop ordering

**Unit 5.**

Use for daily sightseeing loops that begin and end at a base (e.g. accommodation), under a symmetric metric distance matrix.

Algorithm:
1. Validate the metric matrix.
2. Build an MST using a self-implemented Prim or Kruskal/DSU routine.
3. Preorder DFS from the base.
4. Shortcut already visited vertices.
5. Return to base for a closed tour.

The 2-approximation guarantee requires:
- Non-negative distances.
- Symmetry.
- Triangle inequality.
- Complete connected metric over selected stops.

**Road distance issue:** Directed road distances may be asymmetric due to one-way streets. A symmetrised matrix such as `(d(i,j)+d(j,i))/2` may be used only after confirming it is finite and satisfies metric properties for the data. Alternatively use a documented symmetric metric. Keep actual directed route legs separately for navigation. The 2-approximation claim applies to the symmetric metric objective, not automatically to real traffic time.

For open routes with different fixed start/end locations, use an appropriate path heuristic and do not claim the closed-tour guarantee unless its transformation and proof are implemented.

Road trips retain fixed origin/destination/overnight waypoints. Optimise optional detours only between fixed waypoints. Do not reorder fixed waypoints.

Tests: one stop; two stops; known small metric; symmetry; triangle inequality; non-finite matrix; duplicate coordinates; every stop once; closed tour returns to base; fixed-endpoint mode; exact brute-force comparison on small N; 2-approx bound on generated metric cases.

Baselines: nearest neighbour, original order, exact brute force for small instances.

Benchmark 10/20/50/100/200 stops where feasible, reporting MST and traversal costs separately.

## 5.6 Supporting day-allocation and schedule-repair heuristic

This is necessary product logic but is not automatically a syllabus algorithm.

1. Place fixed bookings and mandatory stops first.
2. For every candidate/day/insertion position, calculate added visit, travel and cost burden.
3. Rank feasible insertions by marginal uncovered-interest value and preference.
4. Insert the best feasible activity.
5. Recompute the affected daily route and schedule.
6. If infeasible, remove/replace lower-value optional stops through a bounded repair pass.
7. Report unscheduled activities and uncovered interests.

Respect day start/end times, opening windows, meals, rest, travel buffers, budget, pace and accommodation. State that the multi-day planner is heuristic and does not guarantee global optimum.

---

# 6. End-to-end planning engine

## 6.1 Generation pipeline

1. Validate inputs.
2. Resolve destination, accommodation, start/end and fixed waypoints.
3. Retrieve candidate places from the internal catalogue.
4. Apply hard exclusions (avoid list, inactive records, region, accessibility, reliable closures/seasonal limits).
5. Calculate transparent TripWeaver relevance scores.
6. Run category-aware Randomized Quickselect.
7. Use Trie for autocomplete and manual place additions.
8. Build weighted experience universe.
9. Run Weighted Set Cover accelerated by Binary Max Heap.
10. Allocate selected activities across days.
11. Build per-day distance matrices.
12. Run Double-Tree TSP for eligible closed daily tours.
13. Retrieve directed road legs/route geometry.
14. Calculate visit, travel, meal and rest time.
15. Validate daily and trip-wide budget/time.
16. Repair or regenerate if infeasible.
17. Return itinerary with explanations, source/confidence labels and uncovered constraints.
18. Persist itinerary and algorithm run metadata.

## 6.2 Recommendation explanations

For each selected place, explain:
- Which requested interests it covers.
- Relevance score breakdown.
- Estimated visit duration and basis.
- Cost estimate and basis.
- Distance/detour contribution.
- Any missing/unverified information.

## 6.3 Hard versus flexible constraints

Hard constraints:
- User must-visit places.
- User must-avoid places.
- Fixed bookings/time windows.
- Latest arrival time.
- Hard budget ceiling.
- Hard day end time.
- Fixed road-trip waypoints.
- Explicit accessibility needs.

Flexible preferences:
- Interest weights.
- Preferred pace.
- Desired category variety.
- Detour tolerance.
- Preferred time of day.
- Soft budget preference.

If a plan is infeasible, return `INFEASIBLE_PLAN` with conflicts and suggested relaxations. Never silently exceed hard constraints or omit must-visits.

---

# 7. Road-trip planner

- Represent route as ordered fixed waypoints: origin, user stops, overnight stop(s), destination.
- Preserve waypoint order.
- Find optional attractions within detour time/distance limits for each segment.
- Select detours using the same recommendation and experience-selection engine.
- Respect maximum driving hours/day and rest intervals.
- Overnight stop suggestions must be route-feasible and have source/confidence labels.
- Estimate fuel using vehicle mileage and a dated/entered fuel price.
- Toll/accommodation costs are unknown unless sourced or user-entered.
- Generate navigation links by day/segment and respect Google Maps waypoint limits.
- Route provider failure must not destroy the saved itinerary.
- Do not apply sightseeing closed-tour TSP guarantees to a fixed-endpoint road trip.

---

# 8. Shared trips and collaboration

## 8.1 Roles

- Owner: full control, invitations, roles, revocation, delete/archive.
- Editor: add/remove/suggest activities, vote and edit permitted itinerary fields.
- Viewer: read-only trip access.

MVP: secure read-only share link, copy/share, view itinerary, owner revocation.

Enhanced:
- Invite members.
- Activity suggestions and voting.
- Collaborative editing.
- Change history.
- Shared expense records and per-person breakdown.
- Version conflict handling.

## 8.2 Security and consistency

- Use cryptographically secure, unguessable share tokens.
- Allow token revocation and optional expiry.
- Enforce permissions on the backend for every operation.
- Use optimistic locking/version numbers.
- Return conflict for stale edits; never silently overwrite.
- Rate-limit public share endpoints.
- Keep private trip access unguessable and protected.
- Polling is acceptable for MVP; WebSockets are optional later.

---

# 9. Architecture and technology

## 9.1 Stack

| Layer | Technology |
|---|---|
| Frontend | React + TypeScript + Vite |
| Styling | Tailwind CSS |
| Map | MapLibre GL JS |
| Charts | Recharts or similar |
| Backend | Java 21 + Spring Boot (JDK 21 or newer) |
| Algorithms | Custom plain Java implementations |
| Database | PostgreSQL + PostGIS (Supabase hosted, or local docker-compose) |
| Persistence | Spring Data JPA or JDBC |
| Validation | Jakarta Validation |
| Backend tests | JUnit 5 |
| Frontend tests | Vitest + React Testing Library |
| Benchmarking | JMH or documented custom harness |
| Routing | Self-hosted OSRM or another compliant provider |
| Map tiles | Permitted provider or self-hosted tiles |

Use a modular monolith. Do not add microservices, Redis or complex orchestration without a demonstrated requirement.

## 9.2 Repository structure

```text
tripweaver/
├── README.md
├── IMPLEMENTATION.md
├── .gitignore
├── docker-compose.yml
├── docs/
│   ├── PROJECT_OVERVIEW.md
│   ├── CIA_COMPLIANCE.md
│   ├── ARCHITECTURE.md
│   ├── DATA_SOURCES.md
│   ├── DATA_DICTIONARY.md
│   ├── ALGORITHM_DESIGN.md
│   ├── CORRECTNESS.md
│   ├── API_SPEC.md
│   ├── TEST_PLAN.md
│   ├── BENCHMARKS.md
│   ├── SECURITY_AND_PRIVACY.md
│   ├── DEMO_SCRIPT.md
│   ├── CONTRIBUTIONS.md
│   ├── DECISIONS.md
│   ├── PROGRESS.md
│   └── OPEN_QUESTIONS.md
├── data/
│   ├── raw/                 # ignored by Git
│   ├── processed/           # ignored unless small and permitted
│   └── sample/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/tripweaver/
│       │   ├── TripWeaverApplication.java
│       │   ├── config/
│       │   ├── security/
│       │   ├── common/
│       │   ├── user/
│       │   ├── trip/
│       │   ├── collaboration/
│       │   ├── place/
│       │   ├── search/
│       │   ├── recommendation/
│       │   ├── itinerary/
│       │   ├── budget/
│       │   ├── routing/
│       │   ├── external/
│       │   ├── benchmark/
│       │   └── algorithms/
│       │       ├── trie/
│       │       ├── selection/
│       │       ├── setcover/
│       │       ├── heap/
│       │       └── tsp/
│       └── test/java/com/tripweaver/
│           ├── algorithms/
│           ├── integration/
│           ├── api/
│           ├── data/
│           └── planning/
├── frontend/
│   ├── package.json
│   └── src/
│       ├── api/
│       ├── app/
│       ├── components/
│       ├── features/
│       │   ├── discover/
│       │   ├── trip-create/
│       │   ├── itinerary/
│       │   ├── road-trip/
│       │   ├── budget/
│       │   ├── collaboration/
│       │   └── place-details/
│       ├── maps/
│       ├── types/
│       └── utils/
└── scripts/
    ├── import-osm/
    ├── validate-data/
    └── benchmark/
```

## 9.3 High-level flow

```text
React UI
  | REST API / optional WebSocket
  v
Spring Boot (Java)
  +-- User / Trip / Collaboration
  +-- Place Catalogue / OSM ingestion
  +-- Trie Search
  +-- Quickselect Candidate Selection
  +-- Weighted Set Cover + Binary Heap
  +-- Day Allocation / Feasibility Validator
  +-- Metric TSP Tour Ordering
  +-- Budget / Pace
  +-- External Routing / Optional Places Enrichment
  +-- Benchmark / Experiment
  |
  v
PostgreSQL
```


---

# 10. API contract

Use `/api/v1`. Keep request/response DTOs separate from persistence entities.

## 10.1 Place and search APIs

- `GET /api/v1/places/search?q=&destination=&category=`
- `GET /api/v1/places/{placeId}`
- `GET /api/v1/places/nearby?lat=&lon=&radiusKm=&categories=`
- `GET /api/v1/destinations`
- `GET /api/v1/destinations/{destinationId}/coverage`

## 10.2 Trip and itinerary APIs

- `POST /api/v1/trips`
- `GET /api/v1/trips/{tripId}`
- `PATCH /api/v1/trips/{tripId}`
- `POST /api/v1/trips/{tripId}/generate`
- `POST /api/v1/trips/{tripId}/replan`
- `GET /api/v1/trips/{tripId}/itinerary`
- `PATCH /api/v1/trips/{tripId}/itinerary`
- `POST /api/v1/trips/{tripId}/stops`
- `DELETE /api/v1/trips/{tripId}/stops/{stopId}`

## 10.3 Road-trip APIs

- `POST /api/v1/road-trips/plan`
- `POST /api/v1/road-trips/{tripId}/detours`
- `GET /api/v1/road-trips/{tripId}/segments`

## 10.4 Collaboration APIs

- `POST /api/v1/trips/{tripId}/share-links`
- `DELETE /api/v1/trips/{tripId}/share-links/{linkId}`
- `GET /api/v1/shared/{token}`
- `POST /api/v1/trips/{tripId}/members`
- `PATCH /api/v1/trips/{tripId}/members/{memberId}`
- `POST /api/v1/trips/{tripId}/suggestions`
- `POST /api/v1/trips/{tripId}/suggestions/{suggestionId}/votes`

## 10.5 Benchmarks and algorithm metadata

- `POST /api/v1/benchmarks/run`
- `GET /api/v1/benchmarks/{runId}`
- `GET /api/v1/algorithms/metadata`

Return compact algorithm metadata: algorithm name, version, input size, output summary, seed (when randomized), execution duration and relevant constraints. Detailed traces should be optional, not returned by default.

## 10.6 Error format

```json
{
  "errorCode": "BUDGET_INFEASIBLE",
  "message": "The selected activities exceed the hard budget.",
  "details": {
    "estimatedMaximum": 28000,
    "budget": 25000,
    "currency": "INR"
  }
}
```

Use appropriate HTTP statuses: 400 invalid request; 401 unauthenticated; 403 insufficient permissions; 404 unknown entity; 409 version conflict; 413 upload too large; 422 infeasible planning constraints; 429 provider quota exceeded; 500 unexpected error without stack trace disclosure.

---

# 11. Frontend product screens

## 11.1 Home / trip creation

- Plan today.
- Plan a multi-day destination trip.
- Plan a road trip.
- Open a shared trip.
- Recently saved trips.

## 11.2 Preference builder

- Destination and dates.
- Interest categories and importance weights.
- Budget and whether it is per-person or total.
- Transport mode.
- Accommodation/base location.
- Trip pace and per-day pace overrides.
- Party size.
- Must-visit/avoid locations.
- Accessibility and traveller constraints.

## 11.3 Discover places

- Map and list view.
- Category, region and interest filters.
- Trie-powered autocomplete.
- Relevance explanation.
- Data source/confidence badge.
- Price range and cost basis.

## 11.4 Itinerary builder

- Day-by-day timeline.
- Numbered stops and route lines.
- Visit and travel durations.
- Daily and trip budget breakdown.
- Workload and pace indicator.
- Uncovered-interest panel.
- Edit/remove/replace/replan controls.
- Source and uncertainty warnings.

## 11.5 Road-trip planner

- Origin, destination and fixed waypoints.
- Departure/arrival targets.
- Maximum driving hours.
- Overnight stop.
- Detour candidates.
- Segment-wise route and cost estimates.
- Navigation links for each segment.

## 11.6 Shared trip

- Member list and roles.
- Read-only share link.
- Activity suggestions and votes.
- Change history.
- Expense summary.
- Revoke link and manage access.

## 11.7 Algorithm inspector

For the academic demonstration show:
- Trie prefix search and number of indexed terms.
- Quickselect input size, K, seed and selected threshold.
- Set Cover universe, chosen sets and covered/uncovered weights.
- Heap operations or priority recomputations.
- TSP distance matrix assumptions, MST and resulting tour.
- Actual benchmark data and baseline results.

## 11.8 UI quality

- No unknown price shown as ₹0.
- Distinguish estimates from verified costs.
- Show data freshness and source.
- Provide list/timeline fallbacks if map tiles fail.
- Provide loading, empty, error and provider-unavailable states.
- Clearly distinguish hard constraints from flexible preferences.
- Make the itinerary editable.
- Keep the local demo usable without live API credentials.

---

# 12. Database design

Suggested logical tables:

### Catalogue
- `destinations`
- `regions`
- `places`
- `place_categories`
- `place_interest_tags`
- `place_source_records`
- `place_cost_estimates`
- `place_opening_hours` (only sourced records)
- `place_import_batches`

### Trip planning
- `users`
- `trips`
- `trip_preferences`
- `trip_days`
- `itinerary_stops`
- `road_trip_segments`
- `fixed_waypoints`
- `trip_cost_estimates`

### Collaboration
- `trip_members`
- `trip_share_links`
- `activity_suggestions`
- `activity_votes`
- `trip_expenses`
- `trip_change_history`

### Evidence
- `algorithm_runs`
- `benchmark_runs`
- `benchmark_samples`

Use primary/foreign keys, uniqueness constraints and indexes for common queries. Store no Google Places content.

---

# 13. Testing and benchmarking requirements

The CIA guidelines require at least five documented correctness tests per core algorithm, including edge/boundary cases. Target 8–12 or more tests per algorithm.

## 13.1 Trie

Test:
1. Empty trie.
2. Insert and find exact term.
3. Prefix returns multiple matches.
4. Prefix with no match.
5. Case normalization.
6. Duplicate term maps to multiple places.
7. Unicode/diacritic handling.
8. Result limit.
9. Delete/reinsert if supported.
10. Destination/category filtering.

Benchmarks: 1K, 10K, 100K and a larger feasible term catalogue.  
Baseline: linear scan across all names.

## 13.2 Randomized Quickselect

Test:
1. Single element.
2. K = 0.
3. K = N.
4. Invalid K.
5. Empty input.
6. Duplicate scores.
7. Sorted and reverse-sorted inputs.
8. Negative/zero score policy.
9. Many seeds compared with sorting reference.
10. Input immutability.
11. Category-aware candidate preservation.
12. Deterministic tie-breaking.

Benchmarks: 1K, 10K, 100K, 1M candidates where feasible.  
Baseline: sort then select K.

## 13.3 Greedy Weighted Set Cover

Test:
1. Single interest covered by one place.
2. One place covers multiple interests.
3. Overlapping sets.
4. Equal gain/cost tie.
5. Empty universe.
6. Uncoverable interest.
7. Zero/negative cost rejected.
8. Duplicate interest tag normalization.
9. Zero-marginal-gain candidates.
10. Budget/time conflict after selection.
11. Deterministic output.
12. Compare small instances with exhaustive optimum.

Benchmarks: 20, 50, 100, 250, 500 candidates with varying interest universe sizes and overlap.  
Baseline: relevance-only ranking, unweighted greedy and exact brute force for small inputs.

## 13.4 Binary Max Heap / Indexed Heap

Test:
1. Empty heap.
2. One item.
3. Ascending inserts.
4. Descending inserts.
5. Duplicate priorities.
6. Extract in priority order.
7. Increase/decrease priority.
8. Update missing ID.
9. Random operation sequence versus reference.
10. Heap invariant after every operation.
11. Lazy greedy equals full-recomputation Set Cover.
12. Deterministic tie-breaking.

Benchmarks: 1K, 10K, 100K and 500K operations.  
Baseline: repeated sort/full priority recomputation.

## 13.5 Metric TSP Double-Tree

Test:
1. One vertex.
2. Two vertices.
3. Small known metric.
4. Symmetry validation.
5. Triangle inequality validation.
6. Non-finite/unreachable matrix rejection.
7. Duplicate coordinates and zero-distance rule.
8. Every stop visited exactly once.
9. Closed tour returns to base.
10. Fixed-endpoint mode does not claim closed-tour bound.
11. Compare with exact brute-force optimum on small N.
12. 2-approximation bound on generated metric instances.

Benchmarks: 10, 20, 50, 100 and 200 stops where feasible.  
Baseline: nearest neighbour, original input order and exact brute force on small cases.

## 13.6 Integrated planning tests

- Feasible under-budget plan.
- Hard budget exceeded.
- Flexible budget exceeded with warning.
- Insufficient time for all activities.
- Unknown price is not treated as zero.
- Opening-hours conflict.
- Fixed booking remains scheduled.
- Relaxed day has more free time/buffer than packed day.
- Must-visit included or explicitly reported infeasible.
- Must-avoid never included.
- Accommodation is correct base.
- Multi-day geographic grouping avoids unnecessary region hopping.
- Fixed road-trip waypoint order preserved.
- Overnight stop divides driving days.
- Detour respects added travel limit.
- Route provider unavailable; local planner still functions with warning.

## 13.7 Collaboration tests

- Owner can create/revoke link.
- Viewer cannot edit.
- Editor can suggest.
- Member voting rules enforced.
- Private trip access is protected.
- Revoked/expired token rejected.
- Stale update returns conflict.
- Change history is recorded.
- Expense split reconciles.

## 13.8 Benchmark hygiene

- Document hardware, OS, Java/JVM and build version.
- Warm up JVM.
- Use fixed seeds and reproducible input generation.
- At least five repetitions; preferably 10–20.
- Report median and dispersion.
- Keep generation outside timed section.
- Separate algorithm time from network/database/render time.
- Validate correctness before timing.
- Save raw CSV/JSON.
- Generate plots from raw results.
- Never fabricate numbers.

---

# 14. Security, privacy, licensing and reliability

- Keep API keys backend-only.
- Use environment variables and `.env.example`; never commit secrets.
- Validate uploads and impose size/time limits.
- Use parameterized DB access.
- Enforce trip permissions on every backend operation.
- Use cryptographically secure share tokens with expiry/revocation options.
- Rate-limit shared-link and enrichment endpoints.
- Store minimal personal data.
- Do not expose precise user location unless the user supplied it for the planning task.
- Provide trip deletion.
- Do not call the Google Places API, scrape Google Maps or store any Google content.
- Document OSM attribution and ODbL obligations.
- Document provider terms, quotas and pricing.
- The application needs no Google API credentials at all.
- Use provider timeouts and fallback states.
- Do not disclose stack traces or secrets in API errors.

---

# 15. Documentation deliverables

Maintain these files during development:

- `README.md`: overview, features, prerequisites, setup, commands, data import, tests, demo.
- `docs/PROJECT_OVERVIEW.md`: users, problem, scope and success measures.
- `docs/CIA_COMPLIANCE.md`: mapping of requirements to implementation evidence.
- `docs/ARCHITECTURE.md`: system and module diagram.
- `docs/DATA_SOURCES.md`: dataset URLs, licence, dates, fields, provenance and limits.
- `docs/DATA_DICTIONARY.md`: domain fields and definitions.
- `docs/ALGORITHM_DESIGN.md`: formal problem, input/output, pseudocode, proof sketch, complexity, limitations and baseline per algorithm.
- `docs/CORRECTNESS.md`: invariants and test evidence.
- `docs/API_SPEC.md`: endpoints and examples.
- `docs/TEST_PLAN.md`: correctness, integration and edge cases.
- `docs/BENCHMARKS.md`: hardware, method, sizes, raw results and interpretation.
- `docs/SECURITY_AND_PRIVACY.md`: permissions, provider keys, sharing and privacy.
- `docs/DEMO_SCRIPT.md`: eight-minute demonstration.
- `docs/CONTRIBUTIONS.md`: each team member's actual work.
- `docs/DECISIONS.md`: material technical choices.
- `docs/PROGRESS.md`: phase status and recent work.
- `docs/OPEN_QUESTIONS.md`: unresolved decisions and blockers.

Do not write claims in reports before the corresponding feature/test/benchmark actually exists.

---

# 16. Phased implementation roadmap and checklists

**Each phase must end with its acceptance gate.** A checked box means the work exists and has been verified, not merely planned.

## Phase 0 — Repository inspection, scope lock and decisions

Goal: understand the current repository and finalise architectural assumptions.

- [ ] Inspect repository and preserve useful existing work.
- [ ] Read this plan completely.
- [ ] Create `docs/PROGRESS.md`.
- [ ] Create `docs/OPEN_QUESTIONS.md`.
- [ ] Create `docs/DECISIONS.md`.
- [ ] Create `docs/CIA_COMPLIANCE.md`.
- [ ] Confirm Java 21+ and Maven.
- [ ] Confirm Node.js and package manager.
- [ ] Record the five algorithm contracts.
- [ ] Record data providers and licensing risks.
- [ ] Record local development commands.
- [ ] Identify optional API credentials.
- [ ] Ensure raw data/secrets are excluded from Git.

**Acceptance gate:** Architecture, assumptions and milestone dependencies documented. No blocking ambiguity remains for the skeleton.

## Phase 1 — Project skeleton and developer tooling

Goal: clean backend/frontend that build and communicate.

- [ ] Create Spring Boot backend.
- [ ] Create React + TypeScript + Vite frontend.
- [ ] Add formatting and linting.
- [ ] Add JUnit 5.
- [ ] Add Vitest/React Testing Library.
- [ ] Add health endpoint.
- [ ] Verify frontend-to-backend call.
- [ ] Add common API error format.
- [ ] Add `.gitignore` and `.env.example`.
- [ ] Add local PostgreSQL setup.
- [ ] Add minimal README/run commands.
- [ ] Add CI build/test workflow where practical.

**Acceptance gate:** Both applications start locally; API call works; tests run from a clean checkout; no secrets committed.

## Phase 2 — Core algorithms, test-first

Goal: implement all five assessed algorithms in isolated Java modules.

- [ ] Implement Trie.
- [ ] Implement Trie tests and linear reference.
- [ ] Implement seeded Randomized Quickselect.
- [ ] Implement Quickselect tests and sorting reference.
- [ ] Implement Greedy Weighted Set Cover.
- [ ] Implement small exhaustive Set Cover reference.
- [ ] Implement custom Binary Max Heap/Indexed Heap.
- [ ] Implement heap tests and invariant checker.
- [ ] Integrate heap-based lazy greedy.
- [ ] Verify lazy greedy equals full-recomputation greedy.
- [ ] Implement metric validation for TSP.
- [ ] Implement Double-Tree MST + preorder tour.
- [ ] Implement exact small-instance TSP reference.
- [ ] Document pseudocode/complexity.
- [ ] Ensure no Spring/DB/HTTP dependencies in algorithm classes.
- [ ] Record seed/tie-breaking rules.

**Acceptance gate:** All tests pass; all edge cases have defined behavior; Quickselect matches sorting; Set Cover output is valid; heap invariant holds; lazy greedy matches reference; TSP assumptions and approximation checks pass.

## Phase 3 — Place catalogue and real data ingestion

Goal: import real Goa geographic data.

- [ ] Define Place, Destination and Region models.
- [ ] Download documented Goa/India OSM extract.
- [ ] Implement OSM import.
- [ ] Normalize categories/tags.
- [ ] Import relevant points of interest.
- [ ] Import/prepare map geometry and routing data needed by the application.
- [ ] Build destination/neighbourhood mapping.
- [ ] Deduplicate using stable source identifiers.
- [ ] Import curated Goa data.
- [ ] Store field-level source and verification timestamps.
- [ ] Add duration/cost confidence fields.
- [ ] Validate coordinates and destination bounds.
- [ ] Produce accepted/rejected/merged record report.
- [ ] Add appropriately licensed sample fixtures.
- [ ] Document OSM attribution/licensing.
- [ ] Generate catalogue coverage report.

**Acceptance gate:** Real Goa catalogue loads reproducibly; records have provenance; missing data is visible; no restricted review data is bulk-stored.

## Phase 4 — Place discovery and search

Goal: make the catalogue usable.

- [ ] Implement place search API.
- [ ] Integrate Java Trie autocomplete.
- [ ] Add category and interest filters.
- [ ] Add destination/neighbourhood filters.
- [ ] Add nearby-place lookup.
- [ ] Build place detail screen.
- [ ] Show source/confidence labels.
- [ ] Build map/list view.
- [ ] Build autocomplete UI.
- [ ] Benchmark Trie against linear scan.
- [ ] Add API/integration tests.

**Acceptance gate:** User can discover places by prefix/category/area; Trie executes in Java; search works without Google credentials.

## Phase 5 — Recommendation and experience selection

Goal: choose relevant and diverse places.

- [ ] Define interest taxonomy.
- [ ] Define user category/interest weights.
- [ ] Define transparent relevance score.
- [ ] Separate hard filters from soft scores.
- [ ] Implement category-aware Quickselect.
- [ ] Implement Weighted Set Cover.
- [ ] Integrate Binary Max Heap lazy greedy.
- [ ] Preserve rare requested interests.
- [ ] Return selection explanations.
- [ ] Return covered/uncovered categories.
- [ ] Add tie and infeasibility handling.
- [ ] Test against actual Goa fixture.
- [ ] Compare ranking-only baseline.
- [ ] Compare small Set Cover instances with exact solution.
- [ ] Persist algorithm metadata and seed.

**Acceptance gate:** Changing user preferences changes the results; selection is diverse; exclusions are respected; uncovered interests are visible; result is reproducible.

## Phase 6 — Time, budget, pace and multi-day scheduling

Goal: turn selections into a feasible day-wise itinerary.

- [x] Implement total/per-person budget.
- [x] Implement party-size conversion.
- [x] Implement cost basis and confidence.
- [x] Implement unknown-cost treatment.
- [x] Implement activity-duration ranges.
- [x] Implement daily time windows.
- [x] Implement meal/rest buffers.
- [x] Implement opening-hour checks where verified.
- [x] Implement trip-level pace.
- [x] Implement day-level pace overrides.
- [x] Implement workload metrics.
- [x] Implement greedy day allocation/insertion.
- [x] Implement schedule repair.
- [x] Keep mandatory stops fixed.
- [x] Report unscheduled activities/uncovered interests.
- [x] Implement hard vs flexible constraints.
- [x] Test relaxed, balanced and packed plans.

**Acceptance gate:** Hard budget/time constraints are never silently violated; unknown cost is not zero; day timelines fit; infeasible plans explain why; edits can trigger safe re-planning.

## Phase 7 — Daily route ordering and navigation

Goal: turn each day's locations into an efficient route.

- [x] Integrate permitted road-distance/time provider or self-host OSRM.
- [x] Build distance matrix for candidate stops.
- [x] Validate metric assumptions for Double-Tree mode.
- [x] Implement daily closed-tour ordering.
- [x] Keep actual directed road legs separate from symmetric metric objective.
- [x] Preserve user-fixed start/end and mandatory events.
- [x] Support open-route mode without false approximation claims.
- [x] Draw route and numbered stops.
- [x] Generate Google Maps URLs within waypoint limits.
- [x] Add no-route/provider failure handling.
- [x] Test route feasibility and visit uniqueness.
- [x] Compare with nearest-neighbour and exact small-instance optimum.

**Acceptance gate:** Every stop appears exactly once; closed tour returns to base; approximation claim only appears for valid metric inputs; real navigation legs are clearly estimated.

## Phase 8 — Road-trip planner

Goal: plan intercity journeys with fixed waypoints and optional detours.

- [x] Implement road-trip input model.
- [x] Implement origin/destination/fixed waypoint handling.
- [x] Preserve waypoint order.
- [x] Add maximum driving hours/day.
- [x] Add meal/rest break insertion.
- [x] Support user-provided overnight stop.
- [x] Suggest feasible overnight stops where data exists.
- [x] Find attractions within detour thresholds.
- [x] Rank optional detours using the recommendation engine.
- [x] Optimise detours only between fixed waypoints.
- [x] Estimate fuel cost from vehicle assumptions.
- [x] Mark toll/accommodation unknown if not sourced.
- [x] Generate segment/day navigation links.
- [x] Create Mumbai-to-Goa demo.
- [x] Test late arrival, impossible driving day, unreachable stops and provider failure.

**Acceptance gate:** Road trip differs from destination itinerary; fixed stops are preserved; driving/rest constraints are respected; detours remain optional and bounded; estimates have confidence labels.

## Phase 9 — Complete itinerary UI and editing experience

Goal: polished end-to-end product.

- [x] Build home modes: today, multi-day, road trip.
- [x] Build preference wizard.
- [x] Build discovery map/list.
- [x] Build itinerary overview and daily timeline.
- [x] Build budget breakdown.
- [x] Build pace/workload visualisation.
- [x] Build route visualisation.
- [x] Add edit/remove/replace/replan actions.
- [x] Show uncovered interests and constraint warnings.
- [x] Add loading/empty/error/provider states.
- [x] Add preloaded demo scenarios.
- [x] Label all estimates and data confidence.
- [x] Add frontend integration tests.

**Acceptance gate:** Complete user-input-to-itinerary workflow works; user can understand and edit results; demo works without live provider credentials.

## Phase 10 — Shared trips and collaboration

Goal: let travel companions view and contribute.

- [x] Implement trip owner.
- [x] Implement viewer/editor roles.
- [x] Implement secure share-link generation.
- [x] Implement read-only shared page.
- [x] Implement revocation and optional expiry.
- [x] Implement invitation/role update.
- [x] Implement activity suggestions.
- [x] Implement voting and duplicate-vote rule.
- [x] Implement change history.
- [x] Implement optimistic locking.
- [x] Implement shared expenses if time permits.
- [x] Add access-control integration tests.

**Acceptance gate:** Shared read-only link works; revoked link fails; viewers cannot edit; editors have limited permissions; owner can revoke access; stale edits return conflict.

## Phase 11 — No-Google-dependency verification

Goal: prove that TripWeaver works with no Google API and no Google credentials. (This phase replaced "Optional external enrichment" on 2026-11-24, decision D39.)

- [x] Application starts and plans with no Google API key or Google configuration present.
- [x] No Google Places API (or other Google API) requests in backend or frontend code; automated source check.
- [x] No Google SDK or client dependency in `pom.xml` or `package.json`.
- [x] All place discovery uses the TripWeaver catalogue.
- [x] Map renders with the configured OSM-compatible tile style (`VITE_MAP_STYLE_URL`, default OpenFreeMap) and shows OSM attribution.
- [x] Routing uses the self-hosted OSRM provider, or reports clearly that road routing is unavailable and labels the fallback estimate.
- [x] Google Maps navigation URLs are built without credentials and split at the waypoint limit.
- [x] No restricted Google Places content stored (database check).
- [x] Core planning works with every external service off.

**Acceptance gate:** every item above verified by tests or recorded runs; no Google API traffic; core workflow complete without Google.

## Phase 12 — Benchmarks, audit and performance

Goal: produce defensible academic evidence.

- [x] Trie vs linear scan benchmarks.
- [x] Quickselect vs sorting.
- [x] Set Cover vs baselines/exact small-instance reference.
- [x] Heap/lazy greedy vs full priority recomputation.
- [x] TSP vs nearest neighbour/exact small-instance optimum.
- [x] At least three increasing sizes per algorithm.
- [x] Multiple repetitions and JVM warm-up.
- [x] Save raw results.
- [x] Generate charts from raw results.
- [x] Document environment and methodology.
- [x] Verify theoretical complexity against code.
- [x] Run complete backend/frontend test suites.
- [ ] Review data licensing/provider policies.
- [ ] Fix critical defects.
- [x] Confirm no fabricated results or unrun-test claims.

**Acceptance gate:** All core algorithms have tests and reproducible scaled benchmarks; baselines are fair; complexity notes match code; critical tests pass.

## Phase 13 — CIA submission, demo and viva

Goal: clean final submission package.

- [ ] Complete README.
- [ ] Complete 1–2 page project proposal.
- [ ] Complete 5–8 page technical report.
- [ ] Complete architecture diagram.
- [ ] Complete algorithm pseudocode and correctness notes.
- [ ] Complete data attribution/provenance.
- [ ] Complete benchmark tables/plots.
- [ ] Document assumptions, limitations and failure cases.
- [ ] Complete team contribution record.
- [ ] Prepare preloaded demo data.
- [ ] Prepare 8-minute demo script.
- [ ] Rehearse on a clean local setup.
- [ ] Prepare each student for 2-minute viva.
- [ ] Verify clean-clone setup commands.
- [ ] Tag final working version in Git.
- [ ] Capture genuine screenshots and demo evidence.

**Acceptance gate:** Clean checkout builds; complete workflow works offline from optional APIs; examiner can alter inputs; each member can explain their contribution and one algorithm in depth.

---

# 17. Definition of done

## Product
- [x] Plan-today mode works.
- [x] Multi-day destination mode works.
- [x] Road-trip mode works.
- [x] Budget and time constraints work.
- [x] Trip and day pace work.
- [x] Accommodation/base constraints work.
- [x] User can edit and replan.
- [x] Shared read-only link works.
- [x] Map and navigation handoff work.

## Algorithms
- [x] Trie implemented in Java.
- [x] Randomized Quickselect implemented in Java and CO5 demonstrated.
- [x] Greedy Weighted Set Cover implemented in Java.
- [x] Binary Max Heap/Indexed Heap implemented in Java and integrated.
- [x] Metric TSP Double-Tree implemented in Java with assumptions validated.
- [x] No assessed algorithm is delegated to a library/service.
- [x] Pseudocode, correctness notes and complexity exist for every core algorithm.

## Data and providers
- [x] Real Goa place catalogue imported.
- [x] OSM attribution/licensing documented.
- [x] Curated fields have provenance.
- [x] Costs have basis/confidence.
- [x] No Google API dependency anywhere; verified in Phase 11.
- [x] Provider outages do not break core planning.
- [x] Raw data is not committed without permission.

## CIA evidence
- [x] At least five correctness tests per core algorithm; target 8–12.
- [ ] At least three increasing benchmark sizes per core algorithm.
- [x] Baseline comparison per core algorithm.
- [x] Actual benchmark outputs saved.
- [x] Edge/failure cases demonstrated.
- [ ] README and reproducible setup complete.
- [ ] Proposal and technical report complete.
- [ ] Eight-minute demo rehearsed.
- [ ] Each member ready for viva.

---

# 18. Recommended demonstration scenario

Use a preloaded reproducible scenario:

**Four-day Goa trip for three friends, staying near Candolim.**

Preferences:
- Beaches, Goan food, forts/heritage, one adventure activity.
- One relaxed day.
- One packed day.
- Remaining day(s) balanced.
- A hard per-person budget.
- One must-visit place and one must-avoid place.

Eight-minute flow:
1. Enter preferences and constraints.
2. Search a place using Trie autocomplete.
3. Show Quickselect candidate pruning and seed.
4. Show Set Cover selecting an experience-diverse collection.
5. Show heap-based lazy priority selection.
6. Show TSP ordering for a day's stops and compare with baseline.
7. Show schedule, budget, pace and infeasibility warnings.
8. Edit an activity and regenerate the affected day.
9. Open the map/navigation handoff.
10. Show a read-only shared trip.

Keep a local dataset and fixed-seed scenario. Do not rely on downloading large data or calling live external APIs during the demo.

---

# 19. Viva preparation

Every member should understand the product workflow and be prepared to explain at least one algorithm deeply.

Questions:
1. What problem does TripWeaver solve?
2. Which data is real, curated, estimated, provider-sourced or user-entered?
3. Why use a Trie?
4. What is its prefix-search complexity?
5. Why use randomized Quickselect rather than sorting?
6. What are its expected and worst-case complexities?
7. How does the seed support reproducibility?
8. What is the Set Cover universe?
9. What objective does weighted greedy Set Cover optimise?
10. What approximation guarantee applies, and where does it stop applying?
11. What does the heap maintain during lazy greedy?
12. Why is a stale priority an upper bound?
13. What assumptions does Double-Tree TSP require?
14. Why does the 2-approximation not automatically apply to live/asymmetric travel times?
15. How do budget/time constraints affect feasibility?
16. What happens if not all interests fit?
17. How do share permissions work?
18. How does the application behave when an external provider fails?
19. Which benchmark demonstrates improvement over a baseline?
20. What was your personal technical contribution?

---

# 20. Decisions Claude Code must not change casually

1. Java owns all assessed algorithms.
2. The place catalogue is TripWeaver's own (OSM + curated). Google Places API is out of scope; Google Maps appears only as plain navigation links.
3. No scraping or prohibited bulk storage.
4. The combined multi-day planner is heuristic; no global-optimality claim.
5. TSP approximation assumptions must be validated.
6. Hard time/budget constraints are never silently violated.
7. Unknown data stays unknown.
8. Pace is based on workload, not stop count alone.
9. Fixed road-trip waypoints remain fixed.
10. Shared trips enforce permissions server-side.
11. Every core algorithm has correctness tests and scaled benchmarks.
12. Phase acceptance gates are mandatory.

---

# 21. Reference links

- Supplied CIA 3 project guidelines — assessment authority.
- OpenStreetMap: https://www.openstreetmap.org/
- OSM copyright/licence: https://www.openstreetmap.org/copyright
- Geofabrik India extracts: https://download.geofabrik.de/asia/india.html
- Overpass API: https://wiki.openstreetmap.org/wiki/Overpass_API
- Google Maps URLs (navigation handoff, no key): https://developers.google.com/maps/documentation/urls/get-started
- OSRM: https://project-osrm.org/
- MapLibre GL JS: https://maplibre.org/maplibre-gl-js/docs/
- Spring Boot: https://spring.io/projects/spring-boot
- React: https://react.dev/
- JUnit 5: https://junit.org/junit5/
- JMH: https://openjdk.org/projects/code-tools/jmh/

---

# Final implementation instruction

Build TripWeaver as one coherent travel-planning product, not five disconnected algorithm demonstrations. Every assessed algorithm needs a clear responsibility in the real workflow, a Java implementation, correctness evidence, complexity analysis and reproducible benchmarks.

Prioritise a correct end-to-end Goa planning workflow first. Then add road-trip planning and shared-trip collaboration. Optional external enrichment comes only after the core engine works without paid services.

**Do not reduce product or algorithmic quality merely to finish superficial features faster. Do not add algorithms for their own sake. Implement every algorithm the product genuinely needs, and prove that it works.**
