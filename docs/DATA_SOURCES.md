# Data sources

## 1. OpenStreetMap (primary geographic catalogue)

| Item | Value |
|---|---|
| Provider | Geofabrik extract of OpenStreetMap |
| File | `western-zone-261122.osm.pbf` (India, Western Zone), 222,731,899 bytes |
| URL | https://download.geofabrik.de/asia/india/western-zone/western-zone-261122.osm.pbf |
| MD5 | `0b49c3d8ed849554033c041c954d07c3` (matches Geofabrik `.md5`) |
| OSM replication timestamp | 2026-11-22T21:20:48Z |
| Downloaded | 2026-11-23 |
| Licence | Open Database License (ODbL) 1.0, © OpenStreetMap contributors. https://www.openstreetmap.org/copyright |
| Stored in Git? | Raw PBF: **no** (`data/raw/` is git-ignored). Derived Goa extract: **yes**, `data/sample/goa-osm-extract.json` (1.2 MB, ODbL, see `data/sample/README.md`) |

**ODbL obligations met:**
- Attribution "© OpenStreetMap contributors" appears in the README and the UI.
- The derived extract is published in the repository under ODbL.
- OSM-derived fields are kept separate from curated fields through `place_source_records` (`source_type = OSM` vs `OFFICIAL` / `CURATED` / `TRIPWEAVER`).

**Rebuild the extract:**
```bash
curl -L -o data/raw/western-zone-261122.osm.pbf \
  https://download.geofabrik.de/asia/india/western-zone/western-zone-261122.osm.pbf
cd backend
./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.place.ingest.OsmExtractTool \
  -Dexec.args="../data/raw/western-zone-261122.osm.pbf ../data/sample/goa-osm-extract.json"
```
The tool streams the PBF in three passes. It keeps named POIs and settlements inside the bounding box lat 14.85–15.85, lon 73.60–74.40, plus the `boundary=administrative, admin_level=4, name=Goa` relation. On the dev machine it took about 11 s. The public Overpass API and public tile servers are not used for data import.

## 2. Curated official sources

Curated values are stored in `data/curated/goa-curated.json`, one source URL and verification date per field. A value is recorded only if the cited page states it. Where sources disagreed, only the official statement is used and the disagreement is noted.

| Place | Field | Source | Confidence |
|---|---|---|---|
| Basilica of Bom Jesus | Opening hours | https://www.goa.gov.in/places/basilica-church-for-churches/ | VERIFIED |
| Archaeological Museum, Old Goa | Opening hours, Friday closure, ₹10 adult entry | https://www.incredibleindia.gov.in/en/goa/goa/the-archaeological-museum-old-goa | VERIFIED |
| Dudhsagar Falls | Monsoon closure of the vehicle circuit | https://www.goa.gov.in/wp-content/uploads/2024/08/CLOSURE-OF-DUDHSAGAR-WATERFALLS.pdf | CURATED_ESTIMATE (month range inferred) |
| Dudhsagar Falls | Jeep seat ₹500 per person | Press report: https://www.freepressjournal.in/lifestyle/goas-dudhsagar-waterfall-jeep-safaris-back-in-action-after-monsoon-hiatus | CURATED_ESTIMATE |
| Goa State Museum | Hours, free entry | https://museum.goa.gov.in/ | VERIFIED, but **unresolved**: no matching OSM POI, so not imported (no invented coordinates) |

Fees for Reis Magos Fort and Bhagwan Mahavir Sanctuary were researched but **not recorded**: no official source stated them, and third-party sites disagreed.

## 3. TripWeaver curation

- **Travel regions** (`data/curated/goa-regions.json`): 12 regions, each anchored at an OSM settlement. A place joins the nearest anchor. This is a heuristic grouping, not an administrative boundary.
- **Category mapping and default visit durations** (`OsmCategoryMapper`): category-level planning defaults, labelled `CURATED_ESTIMATE` with basis "TripWeaver category default".

## 4. Confidence labels

| Label | Meaning |
|---|---|
| VERIFIED | Stated by an official source (government, operator) at `verified_at` |
| PROVIDER_SOURCED | From a data provider (OSM tag) as of the extract timestamp |
| CURATED_ESTIMATE | TripWeaver estimate or a non-official source, explicitly labelled |
| USER_ENTERED | Supplied by the traveller |
| SYNTHETIC | Generated test or benchmark data, never shown as real |
| UNKNOWN | Not known. **Never treated as zero.** |

## 5. Catalogue coverage (import of 2026-11-23)

From `data/processed/goa-import-report.json` (regenerated on each import):

- **Candidates in bbox:** 5,329. **Accepted inside the Goa boundary:** 5,192. **Rejected outside the boundary:** 70. **Merged duplicates:** 67.
- **Activities:** 3,683. **Accommodation (usable as base):** 1,509.

| Interest | Places | Interest | Places |
|---|---|---|---|
| food | 2,853 | heritage | 121 |
| nightlife | 289 | family | 100 |
| nature | 210 | shopping | 67 |
| sightseeing | 69 | beach | 56 |
| adventure | 53 | viewpoint | 44 |
| wildlife | 41 | spiritual | 33 |
| culture | 26 | water_sports | 10 |

Activities per region: north-coast 885, salcete-coast 666, far-north 534, panaji-old-goa 497, mapusa 399, canacona 293, mormugao 110, ponda 107, quepem-sanguem 72, bicholim 68, sattari 27, mollem 25.

### Known gaps (shown in the UI)

| Gap | Size |
|---|---|
| Activities with no known cost | 3,677 of 3,683. Only 6 have a sourced cost: 4 OSM `fee=no` or `charge`, 1 official, 1 curated estimate |
| Activities with no opening hours | 3,410 (313 places have OSM or official hours) |
| Activities with no description | 3,596 |

Other gaps:
- **Water sports** are thin: 7 places tagged `water_sports`, 10 with the interest overall.
- **Places of worship** are kept only with a notability signal (historic, heritage, wikidata, wikipedia or tourism=attraction). 33 qualify. Many temples and churches without these tags are not in the catalogue.
- **Some landmarks are mapped only as `tourism=attraction`.** Fort Aguada is one: the planner treats it as `sightseeing`, not `heritage`.
- **Goa State Museum** is not an OSM POI candidate (see section 2).

Targets in plan §4.1.D were 80–100 restaurants, 50–70 beaches and viewpoints, 40–60 heritage and 30–50 nature places. All are met by count. Costs and hours are the real coverage gap. Hard-budget plans therefore report incomplete estimates instead of assuming zero.

## 5b. Konkan corridor catalogue (Phase 8)

Used for Mumbai–Goa road trips. Built from the same Geofabrik extract with:

```bash
cd backend
./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.place.ingest.OsmExtractTool \n  -Dexec.args="../data/raw/western-zone-261122.osm.pbf ../data/sample/konkan-osm-extract.json 15.60,72.75,19.10,74.10 -Goa 4"
```

- **Area:** bbox lat 15.60–19.10, lon 72.75–74.10, minus the Goa boundary.
- **Import:** 7,380 candidates, 6,283 accepted (830 places to stay), 102 merged, 995 rejected because they lie inside Goa.
- **Regions:** 12 regions in `data/curated/konkan-regions.json`.
- **Curated facts:** none yet. Hours and costs come only from OSM tags; everything else is unknown.

## 6. Road routing (Phase 7)

- **Engine:** self-hosted OSRM `ghcr.io/project-osrm/osrm-backend:v6.0.0`, car profile, Contraction Hierarchies. Built by `scripts/osrm-prepare.sh` from the same Geofabrik western-zone extract (2026-11-22). The output in `data/osrm/` is git-ignored.
- **Use:** travel-time tables and route geometry. Responses are labelled `OSRM`.
- **Fallback:** when OSRM is not running, the route is labelled `ESTIMATE`: straight-line distance × 1.3 at an assumed speed.
- **Walking:** no foot profile is built, so walking always uses the estimate.
- **Navigation:** Google Maps directions URLs (`api=1`) built from coordinates. No Google API, no key, nothing stored.

## 7. Not used yet

- **Google Places:** optional, Phase 11. Provider-off by default. Nothing stored.
- **Weather:** later phases.
- **Map tiles:** Phase 4.
