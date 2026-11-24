# Return routes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With "Plan the way home", a road trip carries four planned return options (direct, same way back, same road with new stops, through a different town); the picked one is shown, saved, shared and printed.

**Architecture:** A pure `ReturnPlanner` (backend `roadtrip/`) builds the four return `RoadTripRequest`s from the outbound request and result, picks the different town, and runs each through an injected leg planner, catching failures per option. `RoadTripService` calls it after the stay with `RoadTripPlanner.plan` as the leg planner. The frontend adds the checkbox, four selectable cards and the picked option's days; the PDF prints the picked option.

**Tech Stack:** Java 21 / Spring Boot, JUnit 5, MockMvc on H2; React 19 + TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-25-return-routes-design.md`

## Global Constraints

- Leave everything uncommitted (user instruction).
- Option order and kinds: 1 `DIRECT` "Direct", 2 `SAME_WAY` "Same way back", 3 `NEW_STOPS` "Same road, new stops", 4 `DIFFERENT_TOWN` "Through <town>".
- Return departs the day after the stay's `lastDay` (or after the arrival date without a stay), at the outbound `departureTime`.
- Different town: ≥ 40 km from every outbound segment polyline; haversine(B,X) + haversine(X,A) ≤ 1.35 × haversine(B,A); farthest from the outbound route wins, cities before towns on ties.
- `planReturn` default false; `returnChoice` 1–4, default 1, else `400 BAD_RETURN_CHOICE`; a choice whose option has no plan falls back to 1 with a warning.
- Nested return plans never carry `stay` or `returnTrip`.
- No interests ticked = every interest (`OsmCategoryMapper.INTERESTS` at 3.0) for options 3 and 4.
- An option failure never fails the request: `plan = null`, `note` = reason.
- Exact notes: `No new stops found on this road`, `No town off the outbound road within a third more driving`, option 2 fallback `Outbound overnight stops did not fit the return timings; they are plain stops here.`
- One print sheet per page; no `dark:` variants.

## Review Focus

1. Outbound trip without detours or overnights: option 2 equals a plain reversed drive, option 3 says "No new stops found on this road" (Task 1 test).
2. No eligible different town (short trip, e.g. Panaji → Candolim): option 4 has `plan = null` and its note (Task 1 test).
3. Saved `returnChoice: 4` on a trip where option 4 has no plan → option 1 with a warning (Task 2 test).
4. Old saved trips without `returnTrip` render (Task 3 test).
5. Viewers (no edit) see the cards but switching is local only and does not crash (Task 3 test renders without `onPickReturn`).

---

### Task 1: ReturnPlanner (pure) and the return model

**Files:**
- Create: `backend/src/main/java/com/tripweaver/roadtrip/ReturnPlanner.java`
- Modify: `RoadTripRequest.java` (append `Boolean planReturn`, `Integer returnChoice`, `Set<String> avoidTowns`; defaults false, 1, empty)
- Modify: `RoadTrip.java` (append `ReturnTrip returnTrip`; records `ReturnTrip(int chosen, List<ReturnOption> options)`, `ReturnOption(int number, String kind, String label, String via, RoadTrip plan, String note)`; `withReturn(ReturnTrip)`; extend `withStay` for the new component)
- Modify: `RoadTripPlanner.java` (validate `returnChoice` 1..4 → `BAD_RETURN_CHOICE`; `addTowns` skips towns whose `key()` is in `req.avoidTowns()`; constructor passes `null` returnTrip)
- Test: `backend/src/test/java/com/tripweaver/roadtrip/ReturnPlannerTest.java`

**Interfaces:**
- Produces: `static LocalDate ReturnPlanner.departure(RoadTrip outbound)` — `stay.lastDay + 1` or `arrival.toLocalDate() + 1`.
- Produces: `static Optional<Town> ReturnPlanner.differentTown(RoadTrip outbound, LatLon from, LatLon to, List<Town> towns)` using `RoadTripPlanner.distanceToPolyline` on each segment's geometry converted to `[lat, lon]`.
- Produces: `static RoadTrip.ReturnTrip ReturnPlanner.plan(RoadTripRequest out, RoadTrip outbound, List<Town> towns, Function<RoadTripRequest, RoadTrip> leg)`.
  - Leg requests copy the outbound request with: origin = outbound destination (coordinates from the ARRIVE item), destination = outbound origin (coordinates from the first DEPART item), `departureDate = departure(...)`, `stayDays 0`, `planReturn false`, `returnChoice 1`, `stayChoices` empty, `arriveBy` null.
  - Option 1: waypoints none, interests empty. Option 2: waypoints = reverse of [outbound user waypoints, detour places (by placeId), overnight stops (placeId or lat/lon, `overnight=true`)], interests empty; on `ApiException` retry with `overnight=false` and set the fallback note. Option 3: interests (defaulted), `avoid` = outbound avoid ∪ detour place ids ∪ overnight place ids, `avoidTowns` = outbound town-stay keys (from `stayOptions` chosen keys). Option 4: waypoint = the town (name, lat, lon), interests (defaulted).
  - Each `leg.apply` inside try/catch (`RuntimeException`) → `plan = null`, `note = message`. Option 3 "no new stops": its plan has no detours and its overnight names equal option 1's → `plan` kept, `note` set.
  - `chosen` = `out.returnChoice()` if that option has a plan, else 1 (caller adds the warning).

- [ ] **Step 1: Write the failing tests** (`ReturnPlannerTest`, with a fake `leg` that records the requests and returns a canned `RoadTrip` built by a small helper; reuse `RoadTripPlannerTest`'s synthetic corridor helpers if convenient, else plain records):
  - `returnDepartsTheDayAfterTheStay`: stay lastDay 2026-12-16 → 2026-12-17; no stay, arrival 2026-12-15T09:06 → 2026-12-16.
  - `fourOptionsInOrder`: kinds `[DIRECT, SAME_WAY, NEW_STOPS, DIFFERENT_TOWN]`, numbers 1–4.
  - `sameWayReversesStopsAsFixedWaypoints`: outbound user waypoint W, detour D, overnight O → option 2 request waypoints names `[O, D, W]`, O overnight, interests empty.
  - `newStopsAvoidsOutboundPlaces`: option 3 request `avoid` ⊇ {D id, O id}, `avoidTowns` ⊇ chosen outbound town key, interests = all INTERESTS when none.
  - `noNewStopsIsSaid`: fake leg returns no detours and the same overnight as option 1 → option 3 note `No new stops found on this road`.
  - `differentTownIsFarFromTheRouteAndWithinTheCap`: towns near the route (10 km), far but too costly (cap broken), and far within cap → picks the last; with only the first two → option 4 `plan` null, note `No town off the outbound road within a third more driving`.
  - `aFailingOptionDoesNotStopTheOthers`: fake leg throws for option 2 → option 2 plan null with the message, options 1, 3, 4 planned.
  - `badReturnChoice`: `RoadTripPlanner.validate` with `"returnChoice":5` → `BAD_RETURN_CHOICE`.
- [ ] **Step 2: Run** `cd backend && ./mvnw -q test -Dtest=ReturnPlannerTest` — expect compile FAIL.
- [ ] **Step 3: Implement** model changes, `ReturnPlanner`, validation, `avoidTowns` filter; update every `new RoadTrip(...)` call (planner, service catch branch).
- [ ] **Step 4: Run** `./mvnw -q test -Dtest='ReturnPlannerTest,RoadTripPlannerTest,StayPlannerTest'` — expect PASS.

---

### Task 2: Wire into RoadTripService

**Files:**
- Modify: `backend/src/main/java/com/tripweaver/roadtrip/RoadTripService.java`
- Test: `backend/src/test/java/com/tripweaver/api/RoadTripApiTest.java`

**Interfaces:**
- Consumes: `ReturnPlanner.plan`, `RoadTrip.withReturn`.
- After the stay: when `req.planReturn()`, `ReturnPlanner.plan(req, r, towns.all(), leg -> RoadTripPlanner.plan(catalogue.allPlaces(), towns.all(), catalogue::hours, routing, leg))`; when `chosen != req.returnChoice()` add warning `Way home: option <n> could not be planned; showing option 1.`; run-log adds `returnOptions` (count with a plan).

- [ ] **Step 1: Write the failing API tests**:
  - `wayHomeOffersFourOptions`: Mumbai → Candolim, `stayDays 2`, `planReturn true`, interests heritage/beach, `maxDrivingHoursPerDay 6` → `returnTrip.options` size 4 in kind order; option 1 plan's first day date = stay.lastDay + 1; option 1 last ARRIVE name contains "Mumbai"; option 4 `via` non-null and its plan has a WAYPOINT item named `via`; no option plan has `stay` or `returnTrip` non-null.
  - `savedChoiceWithoutAPlanFallsBack`: a short trip Panaji (15.4989, 73.8278) → Candolim with `planReturn true`, `returnChoice 4` → option 4 plan null, `returnTrip.chosen == 1`, warnings contain "could not be planned".
  - `noReturnByDefault`: demo body → `returnTrip` null.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=RoadTripApiTest` — expect FAIL.
- [ ] **Step 3: Implement** the wiring.
- [ ] **Step 4: Run** `./mvnw -q test` — expect PASS.

---

### Task 3: Frontend — checkbox, cards, picked route

**Files:**
- Modify: `frontend/src/types/roadtrip.ts` (`planReturn?`, `returnChoice?`, `avoidTowns?` on the request; `returnTrip?: ReturnTrip | null` on `RoadTrip`; `ReturnTrip`, `ReturnOption`)
- Modify: `frontend/src/features/roadtrip/RoadTripPage.tsx` (form `planReturn: boolean`, label `Plan the way home`, help `Four ways back after the stay`; demo sets it true; `RoadTripView` gains `onPickReturn?: (n: number) => void` and renders `ReturnSection` after the stay; RoadTripPage keeps the pick in `lastRequest.returnChoice` and `result.returnTrip.chosen` without a new request)
- Modify: `frontend/src/features/trips/TripPage.tsx` (owner: picking updates the trip's saved request through the existing save path if one exists; otherwise local only — builder checks `SaveTrip`/trip update API and records a ruling)
- Modify: `frontend/src/test/planFixtures.ts` (`returnFixture()` with four options; option 4 `plan: null`, note set)
- Test: `frontend/src/features/roadtrip/ReturnSection.test.tsx`

**Interfaces:**
- Produces: `ReturnSection({ rt, onPick }: { rt: ReturnTrip; onPick?: (n: number) => void })`: heading `Way home`; one `radio` per option named `<number>. <label>` with summary `<hours(totalDrivingMinutes)> · <km> km · <nights> night(s) · <detours> stop(s)` or its note; unbuildable radios disabled; below, the chosen option's `DayCard`s with headings `Return day N · …`.
- `DayCard` gains `label = 'Day'`.

- [ ] **Step 1: Write the failing tests**:
  - `four options in order with the chosen one shown`: radios named `/^1\. Direct/`, `/^2\. Same way back/`, `/^3\. Same road, new stops/`, `/^4\. Through/`; option 1 checked; a `Return day 1` heading present.
  - `an unbuildable option is disabled with its reason`: option 4 radio disabled; its note visible.
  - `picking switches the shown route without a request`: with `onPick` spy and parent state, clicking option 2 checks it and calls `onPick(2)`; `fetch` not called.
  - `viewers see the picked route`: render without `onPick` → radios disabled, chosen route shown.
  - `old trips without a return render as before`: no `Way home` heading.
  - Demo request body has `planReturn: true` (extend `StaySection.test.tsx`'s demo test).
- [ ] **Step 2: Run** `npx vitest run src/features/roadtrip` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npx vitest run`, `npx tsc -b`, `npm run lint` — expect PASS.

---

### Task 4: PDF

**Files:**
- Modify: `frontend/src/features/roadtrip/RoadTripPrint.tsx` (after the stay: `Way home: <label>` then the chosen plan's days as `Return day N` with Maps links `Open return day N in Google Maps`, then `Budget for the way home`)
- Test: `frontend/src/features/roadtrip/RoadTripPrint.test.tsx`

- [ ] **Step 1: Write the failing test** `the PDF prints the picked way home after the stay`: trip with stay and `returnFixture()` chosen 2 → one sheet; level-2 headings (minus budgets) start `[Day 1, Day 2, Your stay in Candolim, Stay day 1, Stay day 2, Way home: Same way back, Return day 1]`; link `Open return day 1 in Google Maps`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement** (reuse the drive-day print block with a `label` parameter).
- [ ] **Step 4: Run** the whole frontend suite, tsc, lint; restart the local backend; check the Mumbai → Goa demo in Chrome (four cards, switching instant, route 4 through an inland town) and a headless PDF.
