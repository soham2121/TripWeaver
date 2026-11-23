# CIA 3 compliance map

Maps each CIA requirement to evidence in the repository. A row is marked done only when the evidence exists and was verified. Last updated after Phase 10.

| Requirement | Evidence | Status |
|---|---|---|
| Group of 2–3 | `docs/CONTRIBUTIONS.md` | Pending (team names, Q1; Phase 13) |
| Java backend, JS-family frontend | `backend/` (Spring Boot 4, Java 25), `frontend/` (React + TypeScript) | Done (Phase 1) |
| ≥2 syllabus techniques, ≥1 from Units 4–6 | Trie (U4), Set Cover (U5), Metric TSP (U5), Heap (U2), Quickselect (U1/6) | Done (Phase 2) |
| CO5 randomized algorithm | Randomized Quickselect, seeded, `algorithms/selection`. Used in the product for candidate pruning (Phase 5); the seed is reported in every recommendation and itinerary response | Done |
| Original Java implementations | `backend/src/main/java/com/tripweaver/algorithms/**` | Done; `AlgorithmIsolationTest` |
| Algorithms used in the product, not only in tests | Trie: search and autocomplete (Phase 4). Quickselect + Set Cover + Heap: recommendations and itinerary selection (Phases 5–6). Double-Tree: daily stop order with metric check (Phase 7) and road-trip detour order (Phase 8) | Done |
| Real or realistic synthetic data | Real OSM Goa catalogue (5,192 places) plus official curated fields; Konkan corridor (6,283 places). See `docs/DATA_SOURCES.md` | Done (Phases 3, 8) |
| ≥5 correctness tests per core algorithm, including edge cases | `docs/CORRECTNESS.md`: Trie 12, Quickselect 15, Set Cover 15, Heap 10, TSP 12. Also product-level tests (206 backend tests in total) | Done |
| Benchmarks at ≥3 increasing sizes | `docs/BENCHMARKS.md`, raw CSV. Trie done (1K–1M); the other algorithms come in Phase 12 | Partial |
| Baseline comparison | Trie vs linear scan done. Quickselect vs sort, Set Cover vs exact, Heap vs recompute, and TSP vs nearest-neighbour and Held–Karp come in Phase 12 | Partial |
| Working product demo | Home modes and demo scenarios (Phase 9), saved and shared trips (Phase 10). The demo works without provider keys | Done; rehearsal in Phase 13 |
| Reproducible setup | `README.md`. Backend tests run on in-memory H2; Docker is needed only for PostgreSQL and the optional OSRM | Done; clean-clone check in Phase 13 |

## Algorithm contracts

| Algorithm | Unit | Product job | Contract |
|---|---|---|---|
| Trie | 4 | Autocomplete over place names, categories and neighbourhoods | `insert(term, ref)`, `searchPrefix(prefix, limit)`, `delete(term, ref)`, `containsExact(term)`. Keys are normalised (NFKD, case-folded, diacritics stripped, whitespace collapsed); the display spelling is kept. O(m + results). |
| Randomized Quickselect | 1 / 6 | Top-K candidate pruning by relevance score | Seeded random pivot, three-way partition. Does not mutate the input. Returns the selected items, threshold, tie policy and seed. Category quotas reserve places for requested rare interests. Expected O(n), worst case O(n²). |
| Greedy Weighted Set Cover | 5 | Pick a diverse set of places covering the weighted interests | Picks argmax(uncovered weight / cost) at each step. Rejects cost ≤ 0. Stops at full coverage, no positive gain, or the limit. Returns the sequence, gains, and covered and uncovered interests. Heuristic; the ln-factor bound applies only to the set-cover objective. |
| Binary Max Heap / Indexed Heap | 2 | Lazy greedy priority queue for Set Cover | Array-backed. insert, extractMax and updatePriority O(log n); peek O(1); build O(n). Deterministic tie-break by id. Lazy greedy must equal full-recompute greedy. |
| Metric TSP Double-Tree | 5 | Order each day's stops as a closed tour from the base; order road-trip detours between fixed ends | Validates non-negative, symmetric, finite and triangle-inequality inputs. Own Prim MST, preorder DFS, shortcut, return to base. The 2-approximation is claimed only for a validated metric. Open-path mode makes no bound claim. |
