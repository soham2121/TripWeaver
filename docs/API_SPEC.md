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
  "maxEntryCostPerPerson": 500, "seed": 42, "destination": "goa" }
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

Field meanings:
- `fixedStops`: hard. The visit starts exactly at `start` on that day.
- `keepStops`: kept on their day if possible. Repair may drop them to fit a must-visit, and reports it.
- `userCosts`: per person, labelled USER_ENTERED.

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
