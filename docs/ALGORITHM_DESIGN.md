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

**Limitations.** No fuzzy/typo matching. Mid-word matches (e.g. "aguada" for "Fort Aguada") need extra index terms (the place service will index word suffixes in Phase 4).

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
