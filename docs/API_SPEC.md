# API specification

Base path: `/api/v1`. All errors use this body:

```json
{ "errorCode": "NOT_FOUND", "message": "Place not found", "details": { "id": "…" } }
```

Status codes:

| Status | Meaning |
|---|---|
| 400 | Invalid input (`BAD_REQUEST`, `BAD_LIMIT`, `BAD_COORDINATES`, `VALIDATION_FAILED`) |
| 401 | Not signed in |
| 403 | Insufficient permissions |
| 404 | Not found |
| 409 | Version conflict |
| 422 | Infeasible plan (`INFEASIBLE_PLAN`, `BUDGET_INFEASIBLE`) |
| 429 | Provider quota exceeded |
| 500 | Unexpected error (no stack trace) |

## Health
- `GET /health` → `{ "status": "UP", "service": "tripweaver", "time": "…" }`

## Places (Phase 4)

### `GET /places/search`
| Param | Default | Meaning |
|---|---|---|
| q | — | Word-prefix match on place names (Trie) |
| destination | `goa` | Destination slug |
| category | — | Category, e.g. `beach`, `museum`, `cuisine:goan` |
| interest | — | Interest tag, e.g. `heritage` |
| region | — | Region slug, e.g. `north-coast` |
| activitiesOnly | false | Exclude accommodation |
| limit | 20 | 1..100 |
| offset | 0 | |

Response: `{ items: PlaceSummary[], limit, offset, hasMore }`. Items are sorted by name.

`PlaceSummary` fields:
- `id`, `name`, `lat`, `lon`
- `primaryCategory`, `categories[]`, `interests[]`
- `regionSlug`, `regionName`, `neighbourhood`
- `activity`, `visitMinutesMin`, `visitMinutesMax`, `intensity`
- `costs[]`: each has `kind`, `min`, `max`, `currency`, `basis`, `confidence`. An empty list means the cost is **unknown**.
- `hasOpeningHours`, `dataQuality`, `missingFields[]`

### `GET /places/autocomplete?q=&limit=8`
Returns `Suggestion[]`: `{ type, label, value, placeId, detail }`.
- `type` is one of PLACE, CATEGORY, INTEREST, CUISINE, NEIGHBOURHOOD, REGION.
- Facet suggestions come first, then places.

### `GET /places/nearby?lat=&lon=&radiusKm=2&categories=a,b&limit=50`
Returns `[{ place: PlaceSummary, distanceKm }]`, sorted by great-circle distance. `radiusKm` must be in (0, 200].

### `GET /places/{id}`
Returns `PlaceDetail`, which contains:
- `summary`, `description`, `website`, `osmRef`, `osmUrl`, `seasonal`, `accessibility[]`
- `costs[]`, `openingHours[]`, `sources[]`, each with `sourceType`, `sourceUrl`, `confidence`, `verifiedAt` and `note`
- `visitMinutesBasis`, `visitMinutesConfidence`, `attribution`

## Destinations
- `GET /destinations` → `[{ slug, name, bbox[minLat,minLon,maxLat,maxLon], placeCount, activityCount, regions[] }]`
- `GET /destinations/{slug}/coverage` → the latest import report: counts by category, region and interest, missing-field counts, rejected, merged and unresolved curated records, plus attribution.

## Recommendations (Phase 5)

### `POST /recommendations/preview`
Body (`RecommendationRequest`), all fields optional except `interests`:

```json
{ "interests": {"beach": 5, "food": 4}, "baseLat": 15.52, "baseLon": 73.766, "travelMonth": 12,
  "maxEntryCostPerPerson": 500, "dayMinutes": 600, "mustVisit": ["<uuid>"], "avoid": ["<uuid>"],
  "requireWheelchair": false, "regions": ["north-coast"], "poolSize": 60, "maxSelections": 20,
  "quotaPerInterest": 3, "seed": 42 }
```

Response: `{ runId, durationMicros, result }`. `result` contains:
- `seed`
- `pool`: `eligible`, `k`, `threshold`, `partitionRounds`, `reserved`, `shortfalls`
- `selected[]`: `place`, `score` breakdown, `step`, `newlyCovered`, `marginalGain`, `selectionCost`, `ratio`, `mustVisit`, `reason`
- `coverage`: `covered`, `uncovered`, `coveredWeight`, `totalWeight`, `stopReason`, `gainEvaluations`, `fullGreedyGainEvaluations`, `lazyEqualsFull`
- `rankingOnlyBaseline`, `fill[]`, `excluded{}`, `warnings[]`

Errors:
- 400: `NO_INTERESTS`, `UNKNOWN_INTEREST`, `BAD_WEIGHT`, `UNKNOWN_PLACE`
- 422: `INFEASIBLE_PLAN`, with `details.conflicts` and `details.suggestedRelaxations`

### `GET /algorithms/metadata?limit=20`
Returns the five algorithm descriptors plus recent `algorithm_runs` rows (algorithm, version, input size, seed, duration, summary JSON).

## Itineraries (Phase 6)

### `POST /itineraries/generate`
Stateless. Re-planning is the same call with the user's edits (`keepStops`, `fixedStops`, `mustVisit`, `avoid`).

```json
{ "baseLat": 15.5202, "baseLon": 73.7659, "baseName": "Candolim stay",
  "startDate": "2026-12-14", "days": 3, "partySize": 2,
  "interests": {"beach": 5, "food": 4, "heritage": 4},
  "transport": "CAR", "pace": "BALANCED", "dayStart": "09:00", "dayEnd": "19:00", "returnToBase": true,
  "budget": {"amount": 30000, "basis": "TOTAL", "strictness": "HARD"},
  "costs": {"foodPerPersonPerDay": 1200, "accommodationPerNight": 4000,
            "fuelPricePerLitre": 101, "mileageKmPerLitre": 15, "miscBufferPercent": 10},
  "userCosts": {"<placeId>": 250},
  "dayOverrides": [{"day": 2, "pace": "PACKED", "start": "08:00", "end": "21:00", "returnToBase": true}],
  "fixedStops": [{"placeId": "<uuid>", "day": 1, "start": "14:00", "minutes": 90}],
  "keepStops": [{"placeId": "<uuid>", "day": 1}],
  "mustVisit": ["<uuid>"], "avoid": ["<uuid>"], "requireWheelchair": false, "regions": [],
  "maxEntryCostPerPerson": 500, "fillFreeTime": true, "seed": 42, "destination": "goa" }
```

Required fields: `baseLat`, `baseLon` and `interests`.

| Field | Default | Allowed values |
|---|---|---|
| `days` | 1 | 1..14 |
| `partySize` | 1 | 1..20 |
| `transport` | CAR | CAR, SCOOTER, TAXI, WALK |
| `pace` | BALANCED | RELAXED, BALANCED, PACKED |
| `dayStart` / `dayEnd` | 09:00–19:00 | |
| `startDate` | today (Asia/Kolkata) | |
| `fillFreeTime` | true | false = schedule only fixed, kept and must-visit stops |

Field meanings:
- `fixedStops`: hard. The visit starts exactly at `start` on that day.
- `keepStops`: kept on their day if possible. Repair may drop them to fit a must-visit, and reports it.
- `userCosts`: per person, labelled USER_ENTERED.
- `fillFreeTime: false`: no new suggestions are added. The UI uses it to remove or move a stop without a replacement appearing.

Response: `{ runId, durationMicros, itinerary }`. `itinerary` contains:
- `days[]`: `day`, `date`, `dayOfWeek`, `start`, `end`, `pace`, `returnToBase`, `endOfDay`, `returnTravelMinutes`, `stops[]`, `meals[]`, `workload`, `cost`, `warnings[]`.
  - `stops[]`: `order`, `place`, `arrive`, `start`, `end`, `visitMinutes`, `visitBasis`, `travelMinutes`, `travelKm`, `waitMinutes`, `kind`, `covers[]`, `cost`, `hours`, `reason`.
    - `kind` is one of FIXED, KEEP, MUST_VISIT, COVER or FILL.
    - `cost` has `min`, `max`, `unknown`, `confidence` and `note`; `min` and `max` are null when the cost is unknown.
    - `hours` has `raw`, `confidence` and `status` (CHECKED, UNPARSED or UNKNOWN).
  - `meals[]`: `label`, `start`, `end`, `place`. `place` is set when a food stop serves as the meal.
  - `workload`: `windowMinutes`, `activityMinutes`, `travelMinutes`, `mealMinutes`, `bufferMinutes`, `waitMinutes`, `freeMinutes`, `utilisation`, `stops`, `highIntensityStops`, `regionTransitions`, `intensityLoad`, `label`.
- `budget`:
  - Fields: `currency`, `partySize`, `lines[]`, `knownMin`, `knownMax`, `unknownItems[]`, `complete`, `limit`, `limitBasis`, `strictness`, `status`, `knownMaxPerPerson`.
  - Each line has `category`, `item`, `min`, `max`, `confidence`, `basis` and `note`.
  - Line categories: ENTRY_FEES, ACTIVITIES, ENTRY_OR_ACTIVITY, FOOD, ACCOMMODATION, FUEL, LOCAL_TRANSPORT, MISC_BUFFER.
  - Status values: WITHIN_BUDGET, INCOMPLETE, OVER_BUDGET (flexible budgets only), NO_LIMIT, NO_LIMIT_INCOMPLETE.
- `coverage`: `requested`, `covered`, `uncovered` and `reasons{interest → why}`.
- Reporting fields: `unscheduled[]` (`placeId`, `name`, `kind`, `reason`), `repairs[]` (`action`, `placeId`, `name`, `day`, `reason`), and `warnings[]`.
- Metadata: `travelBasis`, `disclaimer`, `seed`, and `stats` (`candidates`, `insertionEvaluations`, `repairs`, `recommendationEligible`).

Errors:
- 400: `BAD_BASE`, `BAD_DAYS`, `BAD_PARTY`, `BAD_DAY_WINDOW`, `BAD_BUDGET`, `BAD_COST`, `BAD_OVERRIDE`, `BAD_FIXED_STOP`, `BAD_KEEP_STOP`, plus the recommendation codes.
- 422: `INFEASIBLE_PLAN`, with `details.conflicts` and `details.suggestedRelaxations`.
- 422: `BUDGET_INFEASIBLE`, with `details.estimatedMaximum`, `details.budget` and `details.currency`.

### Phase 7 additions to `itinerary`
- Top level:
  - `travelProvider`: `OSRM` or `ESTIMATE`.
  - `travelBasis`: describes how travel times were computed.
  - Routing fallbacks and unroutable legs appear in `warnings`.
- `days[].ordering`: null when the day has no stops.
  - `applied`: DOUBLE_TREE, INSERTION or TRIVIAL.
  - `method`: `double-tree` or `double-tree-open-path`.
  - Costs, in minutes on the symmetrised matrix: `insertionCost`, `doubleTreeCost`, `mstCost`, `nearestNeighbourCost` (closed tours only), `exactCost` (closed tours with ≤ 9 stops).
  - `metric`, `guaranteeApplies` (closed tour and metric matrix only), `metricViolations[]` and `note`.
- `days[].route`: null when the day has no stops.
  - `provider`, `basis`.
  - `geometry`: `[[lon, lat], …]`.
  - `legs[]`: `{minutes, km}`, driving time only.
  - `totalMinutes`, `totalKm`, `warnings[]`.
  - A closed day has `stops + 1` legs and ends at the base.
- `days[].navigationUrls[]`: Google Maps directions URLs with at most 9 waypoints each, chained (each segment starts where the previous one ended). Travel mode is `driving`, or `walking` for WALK.

## Road trips (Phase 8)

### `POST /road-trips/plan`
Stateless. Places come from every destination catalogue (Goa and the Konkan corridor).

```json
{ "origin": {"name": "Mumbai (CSMT)", "lat": 18.9398, "lon": 72.8355},
  "destination": {"name": "Candolim", "lat": 15.5202, "lon": 73.7659},
  "waypoints": [{"name": "Ratnagiri", "lat": 16.9934, "lon": 73.2954, "overnight": true},
                {"placeId": "<uuid>", "stayMinutes": 60}],
  "departureDate": "2026-12-14", "departureTime": "07:00", "arriveBy": "2026-12-15T18:00",
  "maxDrivingHoursPerDay": 7, "maxContinuousDrivingMinutes": 120, "restBreakMinutes": 15, "mealMinutes": 45,
  "dayStart": "08:00", "dayEnd": "20:00",
  "interests": {"heritage": 5, "beach": 4}, "maxDetourMinutes": 45, "maxDetours": 3, "maxTotalDetourMinutes": 180,
  "suggestOvernight": true, "partySize": 2, "transport": "CAR",
  "mileageKmPerLitre": 15, "fuelPricePerLitre": 101, "tollsTotal": 900, "accommodationPerNight": 3500,
  "foodPerPersonPerDay": 800, "avoid": ["<uuid>"], "seed": 42 }
```

Request rules:
- `origin` and `destination` are required.
- Each waypoint needs `lat`/`lon` or a `placeId`.
- Waypoints are never reordered.
- Without `interests` the plan has no detours.

Response: `{ runId, durationMicros, roadTrip }`. `roadTrip` contains:
- Trip totals: `departure`, `arrival`, `totalDrivingMinutes`, `totalKm`, `nights`, `detourCandidates`.
- `days[]`:
  - Fields: `day`, `date`, `start`, `end`, `drivingMinutes`, `km`, `longestContinuousDrivingMinutes`, `items[]`, `overnight`, `navigationUrls[]`.
  - Each item has `type`, `name`, `placeId`, `lat`, `lon`, `start`, `end`, `minutes`, `km` and `note`.
  - Item types: DEPART, DRIVE, REST, MEAL, DETOUR, WAYPOINT, OVERNIGHT, ARRIVE.
  - `overnight`: `name`, `placeId`, `lat`, `lon`, `kind` (USER or SUGGESTED), `confidence` and `note`.
- `segments[]`, one per pair of consecutive fixed waypoints:
  - Fields: `index`, `from`, `to`, `directMinutes`, `directKm`, `detours[]`, `ordering`, `route` (OSRM or estimate geometry through the visited points).
  - Each detour: `place`, `addedDriveMinutes`, `visitMinutes`, `covers[]`, `score`, `reason`.
  - `ordering`: `method`, `applied`, `routeOrderMinutes`, `doubleTreeMinutes`, `guaranteeApplies` (always false here).
- `budget`: same shape as itinerary budgets. Line categories: FUEL, TOLLS, ACCOMMODATION, FOOD, LOCAL_TRANSPORT, ENTRY_FEES, ENTRY_OR_ACTIVITY.
- Labels: `warnings[]` (dropped detours, uncovered interests, routing fallback), `travelProvider`, `travelBasis`, `disclaimer`, `seed`.

Errors:
- 400: `BAD_WAYPOINT`, `UNKNOWN_PLACE`, `BAD_TRANSPORT` (walking), `BAD_DRIVING_LIMIT`, `BAD_BREAK`, `BAD_DAY_WINDOW`, `BAD_DETOUR_LIMIT`, `BAD_PARTY`, `BAD_COST`.
- 422: `INFEASIBLE_PLAN`, with `details.conflicts` and `details.suggestedRelaxations`. A late arrival also includes `details.estimatedArrival`. Causes:
  - No road between two waypoints.
  - No place to stay reachable within the day's limits.
  - Late arrival at an overnight waypoint.
  - Arrival after `arriveBy`.

## Accounts (Phase 10)

Username + password with a session cookie (`JSESSIONID`, HttpOnly, SameSite=Lax, 12 h). Send requests with `credentials: 'include'`.

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/auth/register` | `{username, password, displayName?}` | 201 `{id, username, displayName}`, signed in |
| POST | `/auth/login` | `{username, password}` | 200 user. 401 `BAD_CREDENTIALS`, the same answer for an unknown user or a wrong password. 429 `RATE_LIMITED` |
| POST | `/auth/logout` | | 204 |
| GET | `/auth/me` | | 200 user, or 401 `UNAUTHENTICATED` |

Validation: `BAD_USERNAME` (3–40 characters `[a-z0-9_.-]`, case-insensitive), `BAD_PASSWORD` (8–100 characters), and 409 `USERNAME_TAKEN`.

## Trips and collaboration (Phase 10)

Every `/trips/**` call needs a session; without one it gets 401 `UNAUTHENTICATED`. The caller's role on the trip is checked on every operation:
- A trip you are not on returns 404, so trip ids reveal nothing.
- Insufficient role returns 403 `FORBIDDEN`.

| Method | Path | Minimum role | Notes |
|---|---|---|---|
| POST | `/trips` | signed in | `{title, kind: ITINERARY or ROAD_TRIP, request, result?}`. Returns 201 TripView with version 0 |
| GET | `/trips` | signed in | Trips you own or are a member of: `{id, title, kind, role, ownerUsername, version, updatedAt}` |
| GET | `/trips/{id}` | viewer | TripView: `{id, title, kind, role, owner, members[], version, request, result, createdAt, updatedAt}` |
| PATCH | `/trips/{id}` | editor; owner for `title` | `{version, title?, request?, result?}` |
| POST | `/trips/{id}/generate` | editor | `{version, request?}`. Runs the planner on `request` (or the saved request) and saves both |
| DELETE | `/trips/{id}` | owner | Archives the trip. Returns 204 |
| POST | `/trips/{id}/members` | owner | `{username, role: EDITOR or VIEWER}`. Returns 201. Errors: 404 for an unknown user, 400 `OWNER_IS_MEMBER`, 400 `BAD_ROLE`, 409 `ALREADY_MEMBER` |
| PATCH | `/trips/{id}/members/{userId}` | owner | `{role}` |
| DELETE | `/trips/{id}/members/{userId}` | owner | Returns 204 |
| POST | `/trips/{id}/share-links` | owner | `{expiresInHours?}` (1..8760). Returns 201 `{id, token, createdAt, expiresAt}`; the token is shown only here |
| GET | `/trips/{id}/share-links` | owner | `[{id, createdAt, expiresAt, revokedAt, active}]`, without tokens |
| DELETE | `/trips/{id}/share-links/{linkId}` | owner | Revokes the link. Returns 204 |
| GET | `/shared/{token}` | public | `{title, kind, result, updatedAt}` only: no request, no members. An invalid, revoked or expired token gets 404 `SHARE_LINK_INVALID`. Rate-limited: 60 requests per minute per IP, then 429 |
| POST | `/trips/{id}/suggestions` | editor | `{placeId, day?, note?}`. Returns 201 Suggestion |
| GET | `/trips/{id}/suggestions` | viewer | `[{id, placeId, placeName, day, note, suggestedBy, createdAt, up, down, score, myVote}]`, highest score first |
| POST | `/trips/{id}/suggestions/{sid}/votes` | editor | `{value: 1 or -1}`. 201 for a new vote, 200 for a changed vote, 409 `DUPLICATE_VOTE` for the same vote again |
| GET | `/trips/{id}/history` | viewer | `[{action, summary, username, version, at}]`, newest first, at most 200 |
| POST | `/trips/{id}/expenses` | editor | `{description, amount, paidBy?}`. `paidBy` defaults to you and must be on the trip |
| GET | `/trips/{id}/expenses` | viewer | `{items[], total, perPerson, balances[{userId, username, paid, share, balance}]}`. A positive balance means others owe this person |

**Optimistic locking:**
- PATCH and generate need the `version` the edit is based on; without it they return 400 `VERSION_REQUIRED`.
- A stale version returns 409 `VERSION_CONFLICT` with `details.currentVersion`, and nothing is overwritten.

**History actions:** CREATED, RENAMED, ITINERARY_UPDATED, REPLANNED, ARCHIVED, MEMBER_ADDED, MEMBER_ROLE_CHANGED, MEMBER_REMOVED, SHARE_CREATED, SHARE_REVOKED, SUGGESTED, EXPENSE_ADDED.

**Not built:**
- The separate `/replan`, `/itinerary` and `/stops` endpoints in IMPLEMENTATION 10.2, and `/road-trips/{id}/detours` and `/segments`. `POST /trips/{id}/generate` with an edited `request` covers these; the UI builds the request as described in D32.
- Accepting a suggestion is done by adding the place as a must-visit ("add to plan").
