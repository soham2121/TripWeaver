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
| 422 | Infeasible plan |
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
