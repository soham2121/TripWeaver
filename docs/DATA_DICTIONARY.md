# Data dictionary

## Catalogue (Flyway `V2__place_catalogue.sql`)

### destinations
| Column | Meaning |
|---|---|
| id | Deterministic UUID from `destination:<slug>` |
| slug, name | e.g. `goa`, `Goa` |
| min/max lat/lon | Bounding box of the OSM boundary polygon |

### regions
TripWeaver travel regions. A place belongs to the region with the nearest anchor settlement.

| Column | Meaning |
|---|---|
| id | UUID from `region:<destination>:<slug>` |
| slug, name | e.g. `north-coast` |
| center_lat/lon | Anchor settlement coordinates (OSM) |

### places
| Column | Meaning |
|---|---|
| id | UUID from `osm:<type>/<id>`. Stable across re-imports |
| osm_type, osm_id | OSM element (node / way / relation). Unique |
| geog | PostgreSQL only (V6): `geography(Point,4326)` generated from longitude/latitude, GiST index `ix_places_geog`. Used by `/places/nearby` |
| name, description | OSM `name`, `description` |
| latitude, longitude | Node position or mean of member nodes for ways and relations |
| neighbourhood | Nearest OSM settlement within 3 km, else null |
| primary_category | See `OsmCategoryMapper` (beach, fort, museum, restaurant, accommodation, …) |
| is_activity | False for accommodation (base location only) |
| visit_minutes_min/max | Planning default; `visit_minutes_confidence` = CURATED_ESTIMATE |
| activity_intensity | LOW / MODERATE / HIGH |
| seasonal_notes, seasonal_closed_months | Curated seasonal closure, e.g. `6,7,8,9` |
| website | OSM `website` / `contact:website` |
| osm_tags_json | Kept OSM tags (whitelist in `OsmPbfExtractor.KEEP_KEYS`) |
| data_quality_status | COMPLETE, or PARTIAL when an activity lacks cost, hours or description |
| missing_fields | Comma list: `cost`, `opening_hours`, `description` |
| is_active | False when the place disappeared from the latest import |
| import_batch_id | Last import that wrote the row |

### place_categories / place_interest_tags / place_accessibility_tags
Sets per place. Categories include `cuisine:<x>` from OSM `cuisine`. Interest taxonomy: `beach, food, nightlife, heritage, culture, spiritual, nature, viewpoint, adventure, water_sports, wildlife, shopping, family, sightseeing`. Accessibility tags come from OSM `wheelchair`, e.g. `wheelchair:yes`.

### place_source_records
Field-level provenance.

| Column | Meaning |
|---|---|
| field_name | e.g. `name,location,category`, `visit_duration`, `opening_hours`, `cost:ENTRY`, `seasonal`, `identity` |
| source_type | OSM, OFFICIAL, CURATED, TRIPWEAVER |
| source_ref | e.g. `node/123` |
| source_url | OSM element URL or official page |
| confidence | See `DATA_SOURCES.md` §4 |
| verified_at | Extract timestamp (OSM) or check date (curated) |

### place_cost_estimates
| Column | Meaning |
|---|---|
| kind | ENTRY, ACTIVITY, … |
| amount_min, amount_max, currency | Range in currency units. Missing rows mean the cost is **unknown, not zero** |
| basis | PER_PERSON, PER_GROUP, PER_VEHICLE, PER_ITEM |
| confidence, source_type, source_url, verified_at, note | Provenance |

### place_opening_hours
Only sourced hours, in OSM `opening_hours` syntax, with confidence, source and verification time.

### place_import_batches
One row per import: source file, OSM timestamp, accepted/rejected/merged counts, full report JSON.

### external_place_references (V5, dropped in V7)
Created for the Google integration that was removed (D39); dropped by V7 (D46).

### Constraints and security (V5, V6)
- Confidence columns accept only VERIFIED, PROVIDER_SOURCED, CURATED_ESTIMATE, USER_ENTERED, SYNTHETIC and UNKNOWN.
- Further checks: latitude and longitude ranges, `visit_minutes_min <= max`, `0 <= amount_min <= amount_max`, a 3-letter currency, and OSM type node, way or relation.
- PostgreSQL: RLS is enabled on every table with no policies, and Supabase `anon`/`authenticated` have no grants. The backend connects as the owner and enforces access itself. New tables must enable RLS too; `scripts/verify-db.sql` checks this.

### Curated import format (`data/curated/<slug>-curated.json`)
- `records[]` are keyed by `osmRef`, with `name` for checking.
- Each record can have `openingHours {raw, confidence, sourceUrl, note}`, `costs[] {kind, min, max, currency, basis, confidence, sourceUrl, note}`, `seasonal {text, closedMonths, sourceUrl}`, a description and accessibility tags.
- The file-level `verifiedAt` applies to each record unless the record overrides it.
- Only facts the cited source states are recorded; anything absent stays unknown.
- Test fixtures in `src/test` are synthetic and never imported.

## Users, trips and collaboration (Flyway `V4__trips_and_collaboration.sql`)

### users
| Column | Meaning |
|---|---|
| username | Lowercase, 3–40 characters `[a-z0-9_.-]`, unique |
| password_hash | BCrypt. The plain password is never stored or returned |
| display_name | Optional |

### trips
| Column | Meaning |
|---|---|
| owner_id | The owner has full control |
| kind | ITINERARY or ROAD_TRIP |
| request_json | Planner input. Carries the user's edits (keepStops, avoid, mustVisit, fillFreeTime) |
| result_json | Last planner result; null until the trip is first planned |
| version | Optimistic lock. Every itinerary or title change increments it, and a stale version gets 409 |
| archived | Set when the owner deletes the trip; archived trips are invisible to everyone |

### trip_members
`(trip_id, user_id)` primary key and `role` (EDITOR or VIEWER). The owner is not a member row.

### trip_share_links
| Column | Meaning |
|---|---|
| token_hash | SHA-256 of a 256-bit random token. The token itself is shown once and never stored |
| expires_at | Optional |
| revoked_at | Set on revocation. Revoked and expired links return 404 |

### activity_suggestions / activity_votes
Each suggestion links a trip to a catalogue place, with an optional `day_number` and note. A vote is +1 or −1, and the primary key `(suggestion_id, user_id)` allows one vote per person.

### trip_expenses
Each row records who paid, a description and an amount (> 0, INR). Expenses are split equally among the owner and all members.

### trip_change_history
Each row records who made a change, an action code, a summary and the trip version after the change. Rows are ordered by an identity id, so changes made in the same millisecond keep their order.
