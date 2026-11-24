# TripWeaver: project proposal

Advanced Algorithms CIA 3, Semester V (2026–27). Solo project with the teacher's permission (D45).

## 1. Problem

Planning a trip to a place like Goa means choosing from thousands of beaches, forts, churches, restaurants and activities. Each has its own opening hours, cost and location. Travellers need to:

- find places that match what they enjoy;
- avoid a plan full of one kind of place (four beaches and no food or heritage);
- split the places across days without breaking opening hours, meal times, pace or budget;
- visit each day's stops in a sensible order, so the time goes on visits rather than driving;
- share the plan with the people they travel with.

Existing map apps answer "what is near me" well but do not build a constrained, multi-day plan. Generic itinerary sites often present invented costs and hours as facts.

## 2. Proposed solution

TripWeaver is a web application with three modes:

1. **Plan today:** one day from where you are staying.
2. **Multi-day trip:** a day-by-day itinerary from interests, base, dates, pace, transport and budget. You can lock, remove, replace, move or add stops, then replan.
3. **Road trip:** fixed stops in the order you give, optional bounded detours, daily driving limits with rests, and suggested overnight stays (Mumbai–Goa corridor).

Saved trips can be shared with companions as editors or viewers, or through a revocable read-only link. The trip page also has place suggestions with votes, split expenses and a change history.

Every cost and fact shows its source and confidence. Unknown costs are shown as unknown, never as ₹0.

## 3. Algorithms (all written in Java, no library implementations)

| Algorithm | Syllabus unit | Job in the product |
|---|---|---|
| Trie | 4 | Prefix autocomplete over about 11,000 place names, categories and areas |
| Randomized Quickselect (CO5) | 1 / 6 | Prune thousands of scored places to a top-K candidate pool in expected O(n), with a reported seed |
| Greedy Weighted Set Cover | 5 | Choose a small, varied set of places that covers the requested interests at low cost |
| Indexed Binary Max Heap | 2 | Lazy-greedy priority queue that makes Set Cover re-evaluate only the candidates whose priority may have changed |
| Metric TSP Double-Tree | 5 | Order each day's stops as a closed tour from the base, with a 2-approximation when the metric is validated |

The multi-day scheduler around them (insertion with repair) is a documented heuristic. The project does not claim it is globally optimal.

## 4. Data

- **Places:** OpenStreetMap (ODbL), imported by an own Java extractor: 5,192 places for Goa and 6,283 for the Konkan corridor.
- **Curated facts:** official entry fees and hours, each with a source URL, a confidence level and a verification date.
- **Road routing:** self-hosted OSRM on the same OSM extract. When OSRM is down, the app uses a labelled straight-line estimate.
- **Map tiles:** OpenFreeMap, with no API key.
- **Database:** PostgreSQL 17 with PostGIS, locally or on Supabase (Mumbai region).
- **No paid or keyed provider:** Google Places is out of scope (D39). Google Maps is used only for plain directions links.

## 5. Technology

- **Backend:** Java (Spring Boot 4, built for Java 21), JDBC and Flyway.
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS and MapLibre GL, with light and dark themes.
- **Tests:** JUnit (216 backend tests, run on in-memory H2) and Vitest (40 frontend tests).

## 6. Evaluation plan

- **Correctness:** 10–15 tests per algorithm, including edge cases and failure inputs (`docs/CORRECTNESS.md`).
- **Benchmarks:** at least three increasing sizes per algorithm, each against a baseline:
  - Trie vs linear scan;
  - Quickselect vs sort;
  - lazy vs full greedy Set Cover, and greedy vs the exact optimum;
  - heap vs linear scan;
  - Double-Tree vs Nearest-Neighbour and Held–Karp.

  Results are in `docs/BENCHMARKS.md`.
- **Product checks:** end-to-end checks in the browser, and a run with every external service switched off.

## 7. Deliverables

A working application, source code, technical report (`docs/REPORT.md`), algorithm design with pseudocode and complexity (`docs/ALGORITHM_DESIGN.md`), benchmarks, data provenance (`docs/DATA_SOURCES.md`), an 8-minute demo script (`docs/DEMO_SCRIPT.md`), and a reproducible setup (`README.md`).
