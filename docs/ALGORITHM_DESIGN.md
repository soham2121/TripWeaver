# Algorithm design

All assessed algorithms live in `backend/src/main/java/com/tripweaver/algorithms/` as plain Java (no Spring, persistence or HTTP). `AlgorithmIsolationTest` enforces this.

| # | Algorithm | Package | Unit | Product job |
|---|---|---|---|---|
| 1 | Trie | `algorithms.trie` | 4 | Autocomplete over place names, categories, cuisines, neighbourhoods |
| 2 | Randomized Quickselect (+ category quotas) | `algorithms.selection` | 1 / 6 (CO5) | Prune scored candidates to a top-K pool |
| 3 | Greedy Weighted Set Cover | `algorithms.setcover` | 5 | Choose a varied set of places that covers the requested experiences |
| 4 | Indexed Binary Max Heap | `algorithms.heap` | 2 | Lazy-greedy priority queue for Set Cover |
| 5 | Metric TSP Double-Tree | `algorithms.tsp` | 5 | Order a day's stops as a closed tour from the base |

---

## 1. Trie

**Problem.** Given N (term, reference) entries, return up to L entries whose normalised term starts with a normalised prefix, optionally filtered.

**Normalisation** (`Trie.normalize`): NFKD → remove combining marks (`\p{M}`) → lower-case (`Locale.ROOT`) → collapse whitespace → trim. So `"Café"` matches `"cafe"`. Edges are Unicode code points (Devanagari, emoji work). Display spelling is stored unchanged.

**Pseudocode**
```
insert(term, ref):
  key ← normalize(term); reject blank
  node ← root
  for each code point c in key: node ← node.child[c] (create if absent)
  if (ref) not already in node.entries: append (term, key, ref)

searchPrefix(prefix, L, filter):
  key ← normalize(prefix); if blank or L = 0 return []
  node ← walk(key); if none return []
  pre-order DFS from node, children in ascending code point:
     emit node.entries passing filter, stop at L

delete(term, ref): walk; remove entry; prune now-empty nodes bottom-up
```

**Ordering.** Key in code-point order (a key before its extensions), then insertion order. Children are `TreeMap` so the order is deterministic.

**Complexity.** Walk O(m log σ) (m = prefix code points, σ = branching, TreeMap lookup). Enumeration proportional to the visited subtree, stopped at L accepted matches (a restrictive filter can visit more). Space O(total key code points + entries).

**Baseline.** `LinearScanIndex`: scan all N entries, `startsWith`, stable sort. O(N·m + h log h) per query.

**Limitations.** No fuzzy/typo matching. Mid-word matches (e.g. "aguada" for "Fort Aguada") need extra index terms. The place search index (Phase 4) also inserts every word-boundary suffix of each name, so "aguada" finds "Fort Aguada".

---

## 2. Randomized Quickselect (CO5)

**Problem.** Given n candidates with finite scores, return the K best. Order: score descending, then tie-break comparator ascending (place id). This is a total order, so the selected set does not depend on the seed. The seed only changes pivots, and so the work done.

**Pseudocode**
```
selectTopK(items, score, tie, K, seed):
  check 0 ≤ K ≤ n; copy items (caller list untouched); reject non-finite scores
  if K = 0: return [] (threshold NaN)
  rng ← SplittableRandom(seed); lo ← 0; hi ← n−1; t ← K−1
  while lo < hi:
    p ← random index in [lo, hi]
    three-way partition [lo, hi] around p: better | equal | worse   (Dijkstra)
    if t < lt: hi ← lt−1   elif t > gt: lo ← gt+1   else break
  sort the first K by the order; threshold ← score of K-th
```

**Category quotas** (`CategoryAwareSelector`). A single global top-K can remove every candidate of a rare interest. For each quota category, in name order: count already chosen members; quickselect the missing number from unchosen members (seed `seed ^ hash(category)`). Then fill the remaining slots by quickselect over everything unchosen. One item may satisfy several quotas. Quotas that cannot be met are returned as `shortfalls`. A quota sum greater than K is rejected.

**Complexity.** Expected O(n) comparisons, worst case O(n²). Random pivots make the worst case unlikely for any fixed input. Output sort O(K log K). Partitioning is in place. The defensive copy costs O(n) space.

**Randomization argument (sketch).** A random pivot lands in the middle half with probability ½, which shrinks the active range to at most ¾. The expected number of rounds per ¾-shrink is ≤ 2, so expected work ≤ 2·n·Σ(¾)^i = 8n = O(n).

**Baseline.** Full sort then take K: O(n log n).

**Seed metadata.** The result carries `seed`, `threshold`, `tiePolicy`, `inputSize` and `partitionRounds`.

---

## 3. Greedy Weighted Set Cover

**Problem.** Universe U of interest tags with weights w_u > 0. Each candidate place p covers S_p ⊆ U and has a selection cost c_p > 0. Choose places to cover the requested tags.

**Rule.** Pick argmax over unused p of `Σ_{u ∈ S_p, u uncovered} w_u / c_p`. Ties go to the smaller id.

**Stop** when covered weight ≥ target × total weight (`TARGET_REACHED`), when the greedy pick limit is reached (`SELECTION_LIMIT`), or when no candidate adds positive weight (`NO_POSITIVE_GAIN`). Must-visit places (`preselected`) are taken first and their tags count as covered.

**Validation.** Rejects costs that are non-finite or ≤ 0, weights ≤ 0, duplicate ids, unknown preselected ids, and duplicate universe tags after normalisation (trim, collapse spaces, lower-case).

**Pseudocode (full recomputation, `GreedyWeightedSetCover`)**
```
take preselected (id order)
loop:
  if covered ≥ target: stop TARGET_REACHED
  if picks = limit: stop SELECTION_LIMIT
  best ← argmax_{unused p, gain>0} gain(p)/c_p  (smaller id on ties)
  if none: stop NO_POSITIVE_GAIN
  take best
```

**Guarantee boundary.** With equal tag weights this is the classical greedy algorithm. Its cost for covering every coverable tag is at most H(d)·OPT, where d is the largest |S_p| and H the harmonic number; tests check this against an exhaustive optimum. With unequal weights, the weights only change the greedy order. The cost bound is then not claimed. Neither result says anything about the full multi-day, budgeted itinerary, which is heuristic.

**Complexity.** O(r · Σ|S_p|) for r picks.

**Baselines.** Exhaustive minimum-cost cover (`ExactSetCover`, n ≤ 20), relevance-only ranking and unweighted greedy (Phase 12 benchmarks).

---

## 4. Indexed Binary Max Heap and lazy greedy

**Structure.** Array-backed binary heap plus a hash map from id to array position. Order: priority descending, then id ascending. `buildFrom` is Floyd's bottom-up construction. It does not use `java.util.PriorityQueue`.

| Operation | Cost |
|---|---|
| insert, extractMax, updatePriority | O(log n) |
| peekMax, contains, priorityOf | O(1) expected |
| buildFrom | O(n) |

**Lazy greedy (`LazyGreedySetCover`).** The heap stores an earlier gain/cost ratio per candidate. Covering more tags can only lower a candidate's uncovered weight, and its cost is fixed and positive. So a stored ratio is an upper bound on the current ratio.
```
heap ← build from initial ratios (gain > 0 only)
each round:
  repeat:
    c ← extractMax; r ← current ratio(c)
    if gain(c) = 0: drop c
    elif heap empty or r > peek.priority or (r = peek.priority and c.id < peek.id): accept c
    else insert(c, r)
```
**Equivalence proof sketch.** Let q be the heap top after popping c. Every other candidate x has true(x) ≤ stored(x) ≤ q.priority. If r > q.priority, c is the unique maximum. If r = q.priority and c.id < q.id, any x with true(x) = r has stored(x) = r ≥ q.priority. So x is at least q in heap order, and therefore x.id ≥ q.id > c.id. Hence c is exactly the candidate full greedy picks. `lazyHeapGreedyEqualsFullRecomputationGreedy` checks this on 400 random instances and also checks that lazy greedy does fewer gain evaluations.

---

## 5. Metric TSP Double-Tree

**Problem.** Closed tour from base b over n stops minimising Σ d(i, j). Distance matrix d is required to be finite, non-negative, symmetric and zero on the diagonal. The triangle inequality is required for the guarantee.

**Pseudocode**
```
validate(d)  — reject if not square/finite/non-negative/zero-diagonal/symmetric
parent ← Prim(d, root = b)            O(n²), ties to lower index
order  ← pre-order DFS of MST from b, children ascending
tour   ← order + [b]
guarantee ← triangle inequality holds
```

**2-approximation proof sketch.** Removing one edge from an optimal tour gives a spanning tree, so MST ≤ OPT. Walking the tree twice costs 2·MST. Shortcutting repeated vertices never increases cost under the triangle inequality. So tour ≤ 2·MST ≤ 2·OPT. Tests check `tour ≤ 2·exact` and `MST ≤ exact` on 150 random Euclidean instances.

**Road distances.** Real directed road times can be asymmetric. They are symmetrised as (d(i,j)+d(j,i))/2 and re-validated before use. The bound then applies to the symmetric objective, not to real traffic time. Directed legs are kept separately for navigation (Phase 7).

**Open path** (`openPath(start, end)`): MST pre-order from start skipping end, then end. No bound is claimed. Fixed road-trip waypoints are never reordered (Phase 8).

**Complexity.** O(n²) time and O(n) extra space for the tour; validation adds O(n³).

**Baselines.** `NearestNeighbourTsp` (O(n²)), input order, and `ExactTsp` (Held–Karp, O(2ⁿn²), n ≤ 16), which is cross-checked against permutation brute force for n ≤ 7.

---

## Seeds and tie rules (summary)

| Algorithm | Determinism |
|---|---|
| Trie | Key code-point order, then insertion order |
| Quickselect | Score desc, then place-id asc; seed affects work only |
| Category quotas | Categories processed by name; per-category seed `seed ^ hash(name)` |
| Set Cover / lazy greedy | Ratio desc, then candidate id asc; tags summed in sorted order (identical floating-point sums in both solvers) |
| Heap | Priority desc, then id asc |
| Double-Tree | Prim picks lowest index on ties; children visited in index order |

---

## Product integration: recommendation pipeline (Phase 5)

`recommendation/RecommendationEngine` (pure) runs these steps:

1. **Hard filters**, which are never traded off against score: avoid list, region restriction, curated seasonal closure in the travel month, and `wheelchair=no` when wheelchair access is required. Must-visit conflicts (also avoided, closed that month, inaccessible) return `422 INFEASIBLE_PLAN` with conflicts and suggested relaxations.
2. **Relevance score** (`ScoreBreakdown`), in [0, 1]:
   - total = 0.45·interest + 0.20·distance + 0.10·budget + 0.10·duration + 0.15·data-confidence.
   - Unknown cost scores a neutral 0.5 and is noted. It is never treated as zero.
   - Places matching no requested interest drop out.
3. **Category-aware Randomized Quickselect.** K = poolSize (default 60). Each requested interest reserves min(3, ⌊K/|U|⌋) slots.
4. **Lazy-greedy Weighted Set Cover** on the pool plus must-visits:
   - Universe = requested interests weighted 1–5.
   - Selection cost = (0.2 + 0.4·duration + 0.2·spend + 0.2·detour) / (0.5 + relevance).
   - Must-visits are preselected.
   - Full greedy also runs; the response reports `lazyEqualsFull` and the gain-evaluation counts of both.
5. **Outputs:**
   - A ranking-only baseline with the same number of places, for comparison.
   - The remaining pool ranked by score ("fill"), which the scheduler (Phase 6) uses.
   - Warnings, including the count of selected places with unknown cost.

Example on the real Goa catalogue, 2026-11-23, Postgres:
- **Request:** beach 5, food 4, heritage 4, adventure 2; base Candolim; December; seed 2026.
- **Quickselect:** 3,083 eligible places → K = 60, in 36 partition rounds.
- **Set Cover:** 4 picks covering all 4 interests.
- **Ranking-only baseline:** 4 beaches, leaving adventure, food and heritage uncovered.
- **Heap:** 118 gain evaluations vs 234 for full recomputation; identical picks.
- **Time:** whole pipeline 36.8 ms.

---

## Supporting heuristic: day allocation and schedule repair (Phase 6)

This is product logic, not an assessed algorithm. It is a heuristic and does not guarantee a globally optimal itinerary. The code is in `planning/ItineraryPlanner` (pure).

**Day simulation** (`simulate(day, order)`): walk the stops in order from the base at the day start. For each stop:
1. Insert a pending meal break if the time has passed the meal time. A food stop next may itself become the meal.
2. Add the travel estimate.
3. Reject the order on a seasonal closure.
4. Wait for a fixed booking time. Reject if it is already late.
5. Move the start to the earliest time that fits a parsed opening-hours window on that weekday. Reject if none fits.
6. Reject if the visit would end after the day end.
7. Add the pace buffer.

After the last stop, return to the base if set and reject if that ends after the day end. The result carries the timeline, meals and workload metrics. One simulation costs O(n) for n stops.

**Placement order**
1. Fixed bookings at their day and time. Any failure → 422 `INFEASIBLE_PLAN`.
2. Kept stops: cheapest feasible position on their own day, else on any day.
3. Must-visits: cheapest feasible position on any day, ignoring pace caps. If there is none, repair: for each day, drop the r = 1..3 lowest-value non-mandatory stops, and keep the day whose dropped value is smallest. If no repair works → 422 with the reasons collected from all simulations.
4. Hard-budget check on fixed costs plus mandatory stops → 422 `BUDGET_INFEASIBLE`.
5. Greedy fill. Each round evaluates every remaining candidate c, day d and position i. An insertion is feasible when the simulation succeeds, the pace caps hold (utilisation, stops, high-intensity stops, food stops), and the known spend stays within a hard budget. The best feasible insertion has the highest value:

   `value = (uncoveredWeight(c) + 0.5·relevance(c)) / (1 + 0.5·sameKindCount) / (1 + burden/120)`

   - `burden` = added (visit + travel + buffer + wait) minutes + 30 × added region transitions.
   - Ties go to the earlier day, earlier position and smaller id.
6. Hard-budget repair: once fuel is known, drop the lowest-value optional stop while the known maximum exceeds the limit.

**Complexity.** Each greedy round costs O(C · D · n²) for C candidates, D days and n stops per day. The 7-day packed demo needed 215,786 simulations and took 115 ms.

**Why it is not optimal.** Insertion is myopic: an early cheap insertion can block a better combination later. Repair is bounded to 3 removals on one day. Stop order is insertion order; Phase 7 reorders each day with Double-Tree.

---

## Product integration: daily route ordering (Phase 7)

`ItineraryPlanner.orderDays()` runs after day allocation and repair.

1. **Travel matrix.** One directed matrix over base + all candidates: OSRM `/table` (CH), or the labelled estimate. The scheduler itself (Phase 6) runs on these directed minutes, so day feasibility uses road times.
2. **Per-day metric instance.** Vertices are the base and the day's stops. Each distance is the symmetrised minutes `(d(i,j) + d(j,i)) / 2`. `MetricValidator` checks every condition, including the triangle inequality (O(n³) with n ≤ 9).
3. **Double-Tree.**
   - A day that returns to base gets `closedTour(sym, base)`.
   - Otherwise `openPath(sym, base, lastStop)`, with no bound claimed.
   - The claim "≤ 2 × optimal" is shown only when the day is closed and the matrix is metric. It concerns the symmetric objective, not directed traffic time.
4. **Feasibility and choice.** The Double-Tree order is re-simulated with directed legs, opening hours, fixed bookings, meals and the day window. It replaces the insertion order only if feasible and shorter.
5. **Baselines in every response:** insertion order, nearest neighbour, and the exact Held–Karp optimum for days with ≤ 9 stops.
6. **Geometry and navigation.** OSRM `/route` returns the day's polyline and directed legs (estimate fallback: straight segments). Google Maps URLs carry ≤ 9 waypoints each and are chained.

Tests cover: each stop visited once, the closed tour returning to base, MST ≤ exact, Double-Tree ≤ 2·exact, no claim on non-metric times, no claim on open paths, fixed booking times kept after reordering, and a routing outage still producing a labelled plan.

---

## Product integration: road trips (Phase 8)

`roadtrip/RoadTripPlanner` (pure; heuristic) runs these steps:

1. **Segments.** Fixed waypoints are kept in the given order. Each consecutive pair is routed directly, which gives the geometry used as the corridor. A pair with no road route makes the trip infeasible.
2. **Corridor candidates.** Distance from each catalogue place to the segment polyline: equirectangular point-to-piece distance over at most 400 vertices. The result also gives the place's position along the route.
   - **Detours:** activities matching the interests within the detour corridor.
   - **Places to stay:** within 5 km, up to 3 nearest per 5% slice of each segment it lies near.
3. **Ranking.** Detour candidates are ranked with Randomized Quickselect and lazy-greedy Weighted Set Cover (the same engine as itineraries; no base location).
4. **Added time.** One travel matrix per segment over {ends, candidates, places to stay}. Added driving = d(A,p) + d(p,B) − d(A,B).
5. **Selection and ordering.** Greedy selection in rank order under three limits: per detour, count, and total time. Within a segment, the order is the shorter of route order and `DoubleTreeTsp.openPath(sym, A, B)`. The endpoints are fixed and no bound is claimed.
6. **Day simulation.**
   - **Driving:** in chunks of at most `maxContinuousDrivingMinutes`, separated by rest breaks; lunch is inserted at 12:30 or at the next stop.
   - **Stops:** visits are checked against parsed opening hours. Waypoint stays and user overnight stops are applied.
   - **Leg that does not fit:** when the next leg would exceed the daily driving limit or the day end, the planner picks the place to stay that fits today and minimises the remaining driving. If none exists, the day is impossible.
7. **Repair.** The plan without detours must be feasible. Detours are then dropped, lowest ranked first, while the plan fails or needs more days than the plan without detours.

**Limits:**
- Roadside rest and lunch locations are approximate.
- Traffic is not modelled.
- Overnight suggestions depend on OSM listing coverage, which is sparse mid-route on NH66.

---

## Product integration: editing and collaboration (Phases 9–10)

There is no new assessed algorithm here. These are the rules that keep the algorithm results consistent when people edit them.

- **Edits re-run the planner** (D32). An edit is turned into planner constraints, and the planner runs again; the plan is never patched on the client. This keeps times, opening hours, budget and Double-Tree ordering valid after every change:
  - Remove: `avoid` plus `keepStops` for every other stop, with no refill.
  - Replace: the same as remove, but with refill.
  - Move: `keepStops` with the new day.
  - Add: `mustVisit`.
  - Replan: only locked stops are kept.
- **Optimistic locking** (D35). Every saved trip has a version. An edit is a single compare-and-set:

  ```
  UPDATE trips SET ..., version = version + 1 WHERE id = ? AND version = ?
  if 0 rows updated: 409 VERSION_CONFLICT (currentVersion)
  ```

  Of two edits based on the same version, exactly one succeeds. The other is told the current version and nothing is overwritten. `TripCollaborationApiTest.staleEditsReturnAConflict` checks this, and so did the live run on PostgreSQL.
- **Duplicate-vote rule.** The primary key `(suggestion_id, user_id)` allows one vote per person per suggestion. The same vote again returns 409; the opposite value updates it.
- **Expense split.** share = total / participants; balance = paid − share. The balances always sum to 0, apart from rounding to the paisa.
