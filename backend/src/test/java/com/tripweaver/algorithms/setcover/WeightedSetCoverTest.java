package com.tripweaver.algorithms.setcover;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeightedSetCoverTest {

    static SetCoverCandidate c(String id, double cost, String... tags) {
        return new SetCoverCandidate(id, Set.of(tags), cost);
    }

    static List<String> ids(SetCoverResult r) {
        return r.steps().stream().map(SetCoverResult.Step::candidateId).toList();
    }

    static SetCoverResult greedy(Map<String, Double> w, List<SetCoverCandidate> cs) {
        return GreedyWeightedSetCover.solve(new SetCoverInstance(w, cs), SetCoverOptions.defaults());
    }

    static SetCoverResult lazy(Map<String, Double> w, List<SetCoverCandidate> cs) {
        return LazyGreedySetCover.solve(new SetCoverInstance(w, cs), SetCoverOptions.defaults());
    }

    @Test
    void singleTagCoveredByOnePlace() {
        SetCoverResult r = greedy(Map.of("beach", 1.0), List.of(c("baga", 2, "beach")));
        assertEquals(List.of("baga"), ids(r));
        assertEquals(Set.of("beach"), r.covered());
        assertTrue(r.uncovered().isEmpty());
        assertEquals(SetCoverResult.StopReason.TARGET_REACHED, r.stopReason());
    }

    @Test
    void multiTagPlaceBeatsSinglesWhenBetterValuePerCost() {
        Map<String, Double> w = Map.of("beach", 1.0, "food", 1.0, "nightlife", 1.0);
        List<SetCoverCandidate> cs = List.of(
                c("baga", 2, "beach", "food", "nightlife"),
                c("shack", 1, "food"),
                c("calangute", 1, "beach"),
                c("club", 1, "nightlife"));
        SetCoverResult r = greedy(w, cs);
        assertEquals(List.of("baga"), ids(r));
        assertEquals(1.5, r.steps().getFirst().ratio(), 1e-12);
    }

    @Test
    void overlappingSetsCountOnlyUncoveredWeight() {
        Map<String, Double> w = Map.of("a", 3.0, "b", 1.0, "c", 1.0);
        List<SetCoverCandidate> cs = List.of(c("p1", 1, "a", "b"), c("p2", 1, "a", "c"));
        SetCoverResult r = greedy(w, cs);
        assertEquals(List.of("p1", "p2"), ids(r));
        assertEquals(1.0, r.steps().get(1).marginalGain(), 1e-12);
        assertEquals(Set.of("c"), r.steps().get(1).newlyCovered());
    }

    @Test
    void equalRatioTieBrokenBySmallerId() {
        Map<String, Double> w = Map.of("a", 1.0, "b", 1.0);
        SetCoverResult r = greedy(w, List.of(c("z", 1, "a"), c("m", 1, "b"), c("q", 2, "a", "b")));
        assertEquals("m", ids(r).getFirst());
    }

    @Test
    void emptyUniverseSelectsNothing() {
        SetCoverResult r = greedy(Map.of(), List.of(c("x", 1, "a")));
        assertTrue(r.steps().isEmpty());
        assertEquals(SetCoverResult.StopReason.TARGET_REACHED, r.stopReason());
    }

    @Test
    void uncoverableInterestIsReported() {
        Map<String, Double> w = Map.of("beach", 1.0, "snow", 2.0);
        SetCoverResult r = greedy(w, List.of(c("baga", 1, "beach")));
        assertEquals(Set.of("snow"), r.uncovered());
        assertEquals(SetCoverResult.StopReason.NO_POSITIVE_GAIN, r.stopReason());
        assertEquals(1.0, r.coveredWeight(), 1e-12);
        assertEquals(3.0, r.totalWeight(), 1e-12);
    }

    @Test
    void invalidCostsAndWeightsRejected() {
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> greedy(Map.of("a", 1.0), List.of(c("x", bad, "a"))));
        }
        assertThrows(IllegalArgumentException.class, () -> greedy(Map.of("a", 0.0), List.of(c("x", 1, "a"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> greedy(Map.of("a", 1.0), List.of(c("x", 1, "a"), c("x", 2, "a"))));
    }

    @Test
    void duplicateTagsAreNormalised() {
        Map<String, Double> w = Map.of(" Beach ", 1.0, "FOOD", 1.0);
        SetCoverResult r = greedy(w, List.of(c("p", 1, "beach", "BEACH ", "food")));
        assertEquals(Set.of("beach", "food"), r.covered());
        assertEquals(2.0, r.steps().getFirst().marginalGain(), 1e-12);
        assertThrows(
                IllegalArgumentException.class,
                () -> greedy(Map.of("beach", 1.0, "BEACH", 2.0), List.of(c("p", 1, "beach"))));
    }

    @Test
    void zeroGainCandidatesNeverSelected() {
        Map<String, Double> w = Map.of("a", 1.0);
        SetCoverResult r = greedy(w, List.of(c("useless", 0.01, "zzz"), c("good", 5, "a")));
        assertEquals(List.of("good"), ids(r));
    }

    @Test
    void selectionLimitAndTargetCoverageStopEarly() {
        Map<String, Double> w = Map.of("a", 1.0, "b", 1.0, "c", 1.0, "d", 1.0);
        List<SetCoverCandidate> cs = List.of(c("1", 1, "a"), c("2", 1, "b"), c("3", 1, "c"), c("4", 1, "d"));
        SetCoverInstance inst = new SetCoverInstance(w, cs);
        SetCoverResult limited = GreedyWeightedSetCover.solve(inst, new SetCoverOptions(1.0, 2, Set.of()));
        assertEquals(2, limited.steps().size());
        assertEquals(SetCoverResult.StopReason.SELECTION_LIMIT, limited.stopReason());
        SetCoverResult half = GreedyWeightedSetCover.solve(inst, new SetCoverOptions(0.5, Integer.MAX_VALUE, Set.of()));
        assertEquals(2, half.steps().size());
        assertEquals(SetCoverResult.StopReason.TARGET_REACHED, half.stopReason());
    }

    @Test
    void preselectedMustVisitsCountAsCovered() {
        Map<String, Double> w = Map.of("a", 1.0, "b", 1.0);
        List<SetCoverCandidate> cs = List.of(c("must", 10, "a"), c("cheapA", 1, "a"), c("cheapB", 1, "b"));
        SetCoverResult r = GreedyWeightedSetCover.solve(
                new SetCoverInstance(w, cs), new SetCoverOptions(1.0, Integer.MAX_VALUE, Set.of("must")));
        assertEquals(List.of("must", "cheapB"), ids(r));
        assertTrue(r.steps().getFirst().preselected());
        assertThrows(
                IllegalArgumentException.class,
                () -> GreedyWeightedSetCover.solve(
                        new SetCoverInstance(w, cs), new SetCoverOptions(1.0, 5, Set.of("unknown"))));
    }

    @Test
    void outputIsDeterministicUnderInputShuffles() {
        Random rnd = new Random(9);
        for (int t = 0; t < 50; t++) {
            SetCoverInstance inst = randomInstance(rnd, 30, 8, false);
            List<SetCoverCandidate> shuffled = new ArrayList<>(inst.candidates());
            Collections.shuffle(shuffled, rnd);
            assertEquals(
                    ids(GreedyWeightedSetCover.solve(inst, SetCoverOptions.defaults())),
                    ids(GreedyWeightedSetCover.solve(
                            new SetCoverInstance(inst.weights(), shuffled), SetCoverOptions.defaults())));
        }
    }

    @Test
    void greedyIsValidCoverAndWithinHarmonicBoundOfExactOptimum() {
        Random rnd = new Random(123);
        double worst = 1.0;
        for (int t = 0; t < 300; t++) {
            SetCoverInstance inst = randomInstance(rnd, 2 + rnd.nextInt(11), 2 + rnd.nextInt(6), true);
            SetCoverResult g = GreedyWeightedSetCover.solve(inst, SetCoverOptions.defaults());
            ExactSetCover.Solution opt = ExactSetCover.minCostCover(inst);

            Set<String> coverable = new HashSet<>();
            inst.candidates().forEach(cand -> coverable.addAll(cand.normalisedTags()));
            coverable.retainAll(inst.normalisedWeights().keySet());
            assertEquals(coverable, g.covered(), "greedy must cover every coverable tag");

            int d = inst.candidates().stream()
                    .mapToInt(cand -> cand.normalisedTags().size())
                    .max()
                    .orElse(1);
            double h = 0;
            for (int i = 1; i <= d; i++) h += 1.0 / i;
            assertTrue(g.totalCost() >= opt.cost() - 1e-9, "exact must not exceed greedy");
            assertTrue(g.totalCost() <= h * opt.cost() + 1e-9, "H(d) bound, equal weights");
            if (opt.cost() > 0) worst = Math.max(worst, g.totalCost() / opt.cost());
        }
        assertTrue(worst >= 1.0);
    }

    @Test
    void lazyHeapGreedyEqualsFullRecomputationGreedy() {
        Random rnd = new Random(31337);
        long fullEvals = 0, lazyEvals = 0;
        for (int t = 0; t < 400; t++) {
            SetCoverInstance inst = randomInstance(rnd, 1 + rnd.nextInt(60), 1 + rnd.nextInt(15), rnd.nextBoolean());
            SetCoverOptions opts = new SetCoverOptions(rnd.nextBoolean() ? 1.0 : 0.7, 1 + rnd.nextInt(20), Set.of());
            SetCoverResult full = GreedyWeightedSetCover.solve(inst, opts);
            SetCoverResult lz = LazyGreedySetCover.solve(inst, opts);
            assertEquals(full.steps(), lz.steps(), "trial " + t);
            assertEquals(full.stopReason(), lz.stopReason());
            fullEvals += full.gainEvaluations();
            lazyEvals += lz.gainEvaluations();
        }
        assertTrue(lazyEvals < fullEvals, "lazy should evaluate fewer gains: " + lazyEvals + " vs " + fullEvals);
    }

    @Test
    void lazyGreedyHandlesTiesExactlyLikeFull() {
        Map<String, Double> w = Map.of("a", 1.0, "b", 1.0, "c", 1.0);
        List<SetCoverCandidate> cs =
                List.of(c("d", 1, "a", "b"), c("b", 1, "b", "c"), c("a", 1, "a", "c"), c("e", 1, "c"));
        assertEquals(ids(greedy(w, cs)), ids(lazy(w, cs)));
    }

    static SetCoverInstance randomInstance(Random rnd, int n, int u, boolean equalWeights) {
        Map<String, Double> w = new LinkedHashMap<>();
        for (int i = 0; i < u; i++) w.put("t" + i, equalWeights ? 1.0 : 1 + rnd.nextInt(5));
        List<SetCoverCandidate> cs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Set<String> tags = new HashSet<>();
            int k = rnd.nextInt(Math.min(u, 4) + 1);
            for (int j = 0; j < k; j++) tags.add("t" + rnd.nextInt(u + 1)); // t{u} is outside the universe
            cs.add(new SetCoverCandidate(String.format("c%03d", i), tags, 1 + rnd.nextInt(9)));
        }
        return new SetCoverInstance(w, cs);
    }
}
