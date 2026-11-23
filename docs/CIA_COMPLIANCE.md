# CIA 3 compliance map

Maps each CIA requirement to evidence in the repository. A row is marked done only when the evidence exists and was verified.

| Requirement | Evidence | Status |
|---|---|---|
| Group of 2–3 | `docs/CONTRIBUTIONS.md` | Pending (team names, Q1) |
| Java backend, JS-family frontend | `backend/` (Spring Boot, Java 25), `frontend/` (React + TypeScript) | Done (Phase 1) |
| ≥2 syllabus techniques, ≥1 from Units 4–6 | Trie (U4), Set Cover (U5), Metric TSP (U5), Heap (U2), Quickselect (U1/6) | Implemented and tested (Phase 2) |
| CO5 randomized algorithm | Randomized Quickselect, seeded, `algorithms/selection` | Implemented and tested; product integration in Phase 5 |
| Original Java implementations | `backend/src/main/java/com/tripweaver/algorithms/**` | Done; `AlgorithmIsolationTest` |
| Real or realistic synthetic data | OSM Goa import, `docs/DATA_SOURCES.md` | Pending (Phase 3) |
| ≥5 correctness tests per core algorithm incl. edge case | `docs/CORRECTNESS.md`: Trie 12, Quickselect 15, Set Cover 15, Heap 10, TSP 12 | Done |
| Benchmarks at ≥3 increasing sizes | `docs/BENCHMARKS.md`, raw CSV | Pending (Phase 12) |
| Baseline comparison | `docs/BENCHMARKS.md` | Pending (Phase 12) |
| Reproducible setup | `README.md`, clean-clone build verified in Phase 1 | Done for skeleton; re-verify at Phase 13 |

## Algorithm contracts

| Algorithm | Unit | Product job | Contract |
|---|---|---|---|
| Trie | 4 | Autocomplete over place names, categories, neighbourhoods | `insert(term, ref)`, `searchPrefix(prefix, limit)`, `delete(term, ref)`, `containsExact(term)`. Normalised (NFKD, case-folded, diacritics stripped, whitespace collapsed) keys; display spelling kept. O(m + results). |
| Randomized Quickselect | 1 / 6 | Top-K candidate pruning by relevance score | Seeded random pivot, three-way partition. Does not mutate input. Returns selected items, threshold, tie policy, seed. Category quotas reserve requested rare interests. Expected O(n), worst O(n²). |
| Greedy Weighted Set Cover | 5 | Pick a diverse set of places covering weighted interests | Picks argmax(uncovered weight / cost) each step. Rejects cost ≤ 0. Stops at full coverage, no positive gain, or limit. Returns sequence, gains, covered/uncovered. Heuristic; ln-factor bound applies only to the set-cover objective. |
| Binary Max Heap / Indexed Heap | 2 | Lazy greedy priority queue for Set Cover | Array-backed. insert/extractMax/updatePriority O(log n), peek O(1), build O(n). Deterministic tie-break by id. Lazy greedy must equal full-recompute greedy. |
| Metric TSP Double-Tree | 5 | Order each day's stops as a closed tour from the base | Validates non-negative, symmetric, finite, triangle inequality. Own Prim MST, preorder DFS, shortcut, return to base. 2-approx claimed only for validated metric. Open-path mode makes no bound claim. |
