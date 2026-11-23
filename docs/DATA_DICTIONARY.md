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
| google_place_id | Reserved for optional enrichment (Phase 11) |
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
