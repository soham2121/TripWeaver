# Correctness evidence

Invariants per algorithm and the tests that check them. Test classes are in `backend/src/test/java/com/tripweaver/algorithms/`. Run them with `cd backend && ./mvnw test`.

## Trie — `trie/TrieTest` (12 tests)

**Invariants**
- Every stored entry is reachable by walking its normalised key.
- No empty leaf remains after a delete.
- `size()` equals the number of distinct (key, ref) pairs.
- Results come in key code-point order, then insertion order.

| Test | Case |
|---|---|
| emptyTrieReturnsNothing | Empty trie (edge) |
| exactTermIsFound | Exact term, proper prefix is not exact |
| sharedPrefixReturnsAllMatchesInKeyOrder | Shared prefixes, ordering |
| prefixWithNoMatchIsEmpty | No match, prefix longer than every key |
| caseAndWhitespaceAreNormalisedButDisplayKept | Case and whitespace normalisation |
| duplicateTermMapsToMultipleRefs | Same term for several places; idempotent re-insert |
| unicodeAndDiacriticsAreHandled | Diacritics, Devanagari, emoji (supplementary code point) |
| resultLimitIsRespected | Limit L, L = 0, L < 0 rejected (boundary) |
| blankPrefixReturnsNothing | Blank prefix; blank term rejected |
| deleteThenReinsert | Delete, prune, missing delete, reinsert |
| filterAppliesBeforeLimit | Destination/category filter |
| matchesLinearScanReferenceOnRandomTerms | 2,000 random terms × 8 prefixes × 4 limits, results identical to `LinearScanIndex` |

## Randomized Quickselect — `selection/RandomizedQuickselectTest` (15 tests)

**Invariants**
- After the partition loop, positions [0, K) hold exactly the K best items under the total order.
- The input list is never mutated.
- The output does not depend on the seed.

| Test | Case |
|---|---|
| singleElement | n = 1 |
| kZeroReturnsEmptyWithNaNThreshold | K = 0 (boundary) |
| kEqualsNReturnsAllSortedDescending | K = n (boundary) |
| invalidKRejected | K < 0, K > n |
| emptyInputWithKZero | Empty input |
| duplicateScoresUseDeterministicIdTieBreak | All scores equal |
| sortedAndReverseSortedInputs | Sorted and reverse-sorted input |
| negativeAndZeroScoresAreOrderedNormally | Negative and zero scores |
| nonFiniteScoreRejected | NaN score |
| manySeedsAgreeWithSortingReference | 200 random trials (random n, K, seed, heavy duplicates) equal sort reference |
| inputIsNotMutated | Immutability |
| sameSeedGivesSameStatsAndAnySeedGivesSameSelection | Reproducibility; seed changes work only |
| categoryQuotaPreservesRareInterest | Global top-K drops heritage; quota keeps it |
| categoryQuotaReportsShortfallAndRejectsOversizedQuotas | Missing category, quota sum > K |
| itemInTwoQuotaCategoriesIsSelectedOnce | Overlapping categories |

## Greedy Weighted Set Cover — `setcover/WeightedSetCoverTest` (15 tests)

**Invariants**
- Covered and uncovered tags partition the normalised universe.
- `coveredWeight` equals the sum of the step gains.
- Each step's gain counts only tags that were uncovered before that step.
- No step has zero gain unless the place was preselected.

| Test | Case |
|---|---|
| singleTagCoveredByOnePlace | One tag |
| multiTagPlaceBeatsSinglesWhenBetterValuePerCost | Multi-tag set |
| overlappingSetsCountOnlyUncoveredWeight | Overlap, marginal gain |
| equalRatioTieBrokenBySmallerId | Tie |
| emptyUniverseSelectsNothing | Empty universe (edge) |
| uncoverableInterestIsReported | Uncoverable interest |
| invalidCostsAndWeightsRejected | Zero, negative, NaN and ∞ costs; zero weight; duplicate id |
| duplicateTagsAreNormalised | Tag normalisation, duplicate universe tags |
| zeroGainCandidatesNeverSelected | Zero marginal gain |
| selectionLimitAndTargetCoverageStopEarly | Selection limit, partial target |
| preselectedMustVisitsCountAsCovered | Must-visit forced in; unknown id rejected |
| outputIsDeterministicUnderInputShuffles | 50 shuffled instances |
| greedyIsValidCoverAndWithinHarmonicBoundOfExactOptimum | 300 instances vs exhaustive optimum; covers all coverable tags; cost ≤ H(d)·OPT |
| lazyHeapGreedyEqualsFullRecomputationGreedy | 400 random instances: identical steps and stop reason; fewer gain evaluations |
| lazyGreedyHandlesTiesExactlyLikeFull | Tie handling in lazy mode |

The "budget/time conflict after selection" case is a planner concern. Hard limits are deliberately kept outside the set-cover objective, so it is tested in the Phase 6 integrated planning tests.

## Indexed Binary Max Heap — `heap/IndexedMaxHeapTest` (10 tests)

**Invariants**
- Every parent ranks at or above its children (priority desc, id asc).
- `pos[a[i].id] = i` for every i.
- `|pos| = |a|`.

`invariantHolds()` is checked after every operation in the randomized test.

| Test | Case |
|---|---|
| emptyHeap | Empty (edge) |
| oneItem | One entry |
| ascendingInsertsExtractDescending | Ascending inserts |
| descendingInsertsExtractDescending | Descending inserts |
| duplicatePrioritiesBreakTiesBySmallerId | Equal priorities |
| extractionFollowsPriorityOrder | Extraction order |
| increaseAndDecreasePriority | Update up and down |
| updateMissingIdAndDuplicateInsertRejected | Missing id, duplicate id, NaN |
| bottomUpBuildProducesValidHeap | Floyd build of 300 entries |
| randomOperationsMatchNaiveReferenceAndKeepInvariant | 5,000 random operations vs naive reference |

Lazy greedy equals full greedy: see the Set Cover tests above.

## Metric TSP Double-Tree — `tsp/DoubleTreeTspTest` (12 tests)

**Invariants**
- A closed tour has n + 1 entries, starts and ends at the base, and visits every stop exactly once.
- MST ≤ OPT.
- On a validated metric, tour ≤ 2·MST ≤ 2·OPT.

| Test | Case |
|---|---|
| oneVertex | n = 1 (edge) |
| twoVertices | n = 2, non-zero base |
| smallKnownMetricSquare | Known optimum (unit square) |
| asymmetricMatrixRejected | Symmetry validation |
| triangleViolationRunsWithoutGuarantee | Triangle inequality validation; guarantee flag false |
| nonFiniteNegativeOrNonSquareRejected | ∞, negative, non-square, non-zero diagonal, bad base, empty |
| duplicateCoordinatesWithZeroDistanceAllowed | Zero distance between distinct stops |
| everyStopVisitedOnceAndTourReturnsToNonZeroBase | 100 random instances |
| openPathKeepsFixedEndpointsAndClaimsNoBound | Fixed-endpoint mode, no bound claim |
| withinTwiceExactOptimumOnMetricInstances | 150 Euclidean instances: OPT ≤ tour ≤ 2·OPT, MST ≤ OPT |
| heldKarpMatchesPermutationBruteForce | Exact reference cross-checked |
| nearestNeighbourBaselineIsValidTour | Baseline validity |

## Isolation — `AlgorithmIsolationTest`

This test fails if any algorithm source imports Spring, Jakarta/JPA or the HTTP client, or uses `PriorityQueue`.
