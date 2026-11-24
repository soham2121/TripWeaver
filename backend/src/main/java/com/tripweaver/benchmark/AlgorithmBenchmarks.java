package com.tripweaver.benchmark;

import com.tripweaver.algorithms.heap.IndexedMaxHeap;
import com.tripweaver.algorithms.selection.RandomizedQuickselect;
import com.tripweaver.algorithms.setcover.ExactSetCover;
import com.tripweaver.algorithms.setcover.GreedyWeightedSetCover;
import com.tripweaver.algorithms.setcover.LazyGreedySetCover;
import com.tripweaver.algorithms.setcover.SetCoverCandidate;
import com.tripweaver.algorithms.setcover.SetCoverInstance;
import com.tripweaver.algorithms.setcover.SetCoverOptions;
import com.tripweaver.algorithms.setcover.SetCoverResult;
import com.tripweaver.algorithms.tsp.DoubleTreeTsp;
import com.tripweaver.algorithms.tsp.ExactTsp;
import com.tripweaver.algorithms.tsp.MetricValidator;
import com.tripweaver.algorithms.tsp.NearestNeighbourTsp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Scaled benchmarks with baselines for Quickselect, Set Cover, Heap and Double-Tree TSP (plan 13.2–13.5).
 * All inputs are SYNTHETIC, generated from fixed seeds outside the timed section. Each implementation is
 * checked against its baseline before timing; a mismatch aborts the run. Solution-quality measurements
 * (not timings) are appended to {@link #quality} and written to their own CSV.
 */
public final class AlgorithmBenchmarks {

    private AlgorithmBenchmarks() {}

    /** Quality rows: algorithm, variant, size, seed, metric, value. */
    public static final List<String> quality = new ArrayList<>();

    private static void q(String alg, String variant, long size, long seed, String metric, double v) {
        quality.add(String.format("%s,%s,%d,%d,%s,%.6f", alg, variant, size, seed, metric, v));
    }

    // ---------------------------------------------------------------- Quickselect vs sort

    public static List<BenchmarkHarness.Sample> quickselect(int[] sizes, int warmup, int reps) {
        List<BenchmarkHarness.Sample> all = new ArrayList<>();
        for (int n : sizes) {
            SplittableRandom r = new SplittableRandom(20261124L + n);
            double[] score = new double[n];
            // Coarse scores (0..999) so ties are common, as with real relevance scores.
            for (int i = 0; i < n; i++) score[i] = r.nextInt(1000);
            List<Integer> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(i);
            int k = Math.max(10, n / 100);
            Comparator<Integer> tie = Comparator.naturalOrder();

            List<Integer> qs = RandomizedQuickselect.selectTopK(ids, i -> score[i], tie, k, 42L)
                    .selected();
            if (!new HashSet<>(qs).equals(new HashSet<>(sortSelect(ids, score, k))))
                throw new IllegalStateException("quickselect differs from sort at n=" + n);
            for (long seed = 1; seed <= 20; seed++) {
                var res = RandomizedQuickselect.selectTopK(ids, i -> score[i], tie, k, seed);
                q("quickselect", "partition-rounds", n, seed, "rounds", res.partitionRounds());
            }

            long[] seed = {0};
            all.addAll(BenchmarkHarness.measure("quickselect", "quickselect", n, warmup, reps, () -> {
                var res = RandomizedQuickselect.selectTopK(ids, i -> score[i], tie, k, ++seed[0]);
                return 1 + (res.selected().size() & 0);
            }));
            all.addAll(BenchmarkHarness.measure("quickselect", "sort-then-select", n, warmup, reps, () -> {
                return 1 + (sortSelect(ids, score, k).size() & 0);
            }));
        }
        return all;
    }

    /** Baseline: full sort (score desc, id asc), take the first k. O(n log n). */
    static List<Integer> sortSelect(List<Integer> ids, double[] score, int k) {
        List<Integer> c = new ArrayList<>(ids);
        c.sort((a, b) -> score[a] != score[b] ? Double.compare(score[b], score[a]) : Integer.compare(a, b));
        return c.subList(0, k);
    }

    // ---------------------------------------------------------------- Set Cover: lazy heap vs full greedy

    static SetCoverInstance setCoverInstance(int n, int universe, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Map<String, Double> w = new LinkedHashMap<>();
        for (int t = 0; t < universe; t++) w.put("t" + t, 1.0 + r.nextInt(5));
        List<SetCoverCandidate> cs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Set<String> tags = new HashSet<>();
            int m = 1 + r.nextInt(5);
            while (tags.size() < m) tags.add("t" + r.nextInt(universe));
            cs.add(new SetCoverCandidate(String.format("p%04d", i), tags, 30 + r.nextInt(211)));
        }
        return new SetCoverInstance(w, cs);
    }

    static List<String> ids(SetCoverResult r) {
        return r.steps().stream().map(SetCoverResult.Step::candidateId).toList();
    }

    public static List<BenchmarkHarness.Sample> setCover(int[] sizes, int[] universes, int warmup, int reps) {
        List<BenchmarkHarness.Sample> all = new ArrayList<>();
        SetCoverOptions opts = SetCoverOptions.defaults();
        for (int u : universes) {
            for (int n : sizes) {
                SetCoverInstance inst = setCoverInstance(n, u, 7000L + 31L * n + u);
                SetCoverResult lazy = LazyGreedySetCover.solve(inst, opts);
                SetCoverResult full = GreedyWeightedSetCover.solve(inst, opts);
                if (!ids(lazy).equals(ids(full)))
                    throw new IllegalStateException("lazy and full greedy differ at n=" + n + " U=" + u);
                q("setcover", "lazy-heap/U" + u, n, 0, "gain_evaluations", lazy.gainEvaluations());
                q("setcover", "full-greedy/U" + u, n, 0, "gain_evaluations", full.gainEvaluations());
                q("setcover", "greedy/U" + u, n, 0, "selected", lazy.steps().size());
                // Relevance-only baseline: cheapest-first until the same coverage, as a quality reference.
                q("setcover", "greedy/U" + u, n, 0, "cost", lazy.totalCost());
                q("setcover", "cheapest-first/U" + u, n, 0, "cost", cheapestFirstCost(inst, lazy.coveredWeight()));

                all.addAll(BenchmarkHarness.measure("setcover", "lazy-heap/U" + u, n, warmup, reps, () -> {
                    return 1 + (LazyGreedySetCover.solve(inst, opts).steps().size() & 0);
                }));
                all.addAll(BenchmarkHarness.measure("setcover", "full-greedy/U" + u, n, warmup, reps, () -> {
                    return 1 + (GreedyWeightedSetCover.solve(inst, opts).steps().size() & 0);
                }));
            }
        }
        // Greedy cost vs exact optimum on small instances (exact is O(2^n · n), n <= 20).
        for (int n : new int[] {12, 16, 20}) {
            for (long seed = 1; seed <= 10; seed++) {
                SetCoverInstance inst = setCoverInstance(n, 12, 900_000L + 100L * n + seed);
                SetCoverResult g = LazyGreedySetCover.solve(inst, opts);
                if (!g.uncovered().isEmpty()) continue; // only fully coverable instances compare with the optimum
                double opt = ExactSetCover.minCostCover(inst).cost();
                q("setcover", "greedy-vs-exact/U12", n, seed, "cost_ratio", g.totalCost() / opt);
            }
        }
        return all;
    }

    /** Baseline ignoring coverage gain: add candidates by ascending cost until the target weight is covered. */
    static double cheapestFirstCost(SetCoverInstance inst, double targetWeight) {
        Map<String, Double> w = inst.normalisedWeights();
        List<SetCoverCandidate> cs = new ArrayList<>(inst.candidates());
        cs.sort(Comparator.comparingDouble(SetCoverCandidate::cost).thenComparing(SetCoverCandidate::id));
        Set<String> covered = new HashSet<>();
        double weight = 0, cost = 0;
        for (SetCoverCandidate c : cs) {
            if (weight >= targetWeight - 1e-9) break;
            boolean adds = false;
            for (String t : c.normalisedTags())
                if (w.containsKey(t) && covered.add(t)) {
                    weight += w.get(t);
                    adds = true;
                }
            if (adds) cost += c.cost();
        }
        return cost;
    }

    // ---------------------------------------------------------------- Heap vs linear-scan priority list

    /** Op codes: 0 insert, 1 extractMax, 2 updatePriority. */
    record Ops(int[] code, int[] id, double[] p) {}

    static Ops ops(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        int[] code = new int[n], id = new int[n];
        double[] p = new double[n];
        int next = 0, live = 0;
        for (int i = 0; i < n; i++) {
            int c = live == 0 ? 0 : r.nextInt(4); // 0,3 -> insert (50%), 1 extract (25%), 2 update (25%)
            if (c == 3) c = 0;
            code[i] = c;
            p[i] = r.nextInt(1_000_000) / 1000.0;
            if (c == 0) {
                id[i] = next++;
                live++;
            } else if (c == 1) live--;
            else id[i] = r.nextInt(next); // may target an extracted id: both structures then skip it
        }
        return new Ops(code, id, p);
    }

    static long runHeap(Ops o, List<Integer> extracted) {
        IndexedMaxHeap<Integer> h = new IndexedMaxHeap<>(Comparator.naturalOrder());
        long sum = 0;
        for (int i = 0; i < o.code.length; i++) {
            switch (o.code[i]) {
                case 0 -> h.insert(o.id[i], o.p[i]);
                case 1 -> {
                    int x = h.extractMax().id();
                    sum += x;
                    if (extracted != null) extracted.add(x);
                }
                default -> {
                    if (h.contains(o.id[i])) h.updatePriority(o.id[i], o.p[i]);
                }
            }
        }
        return sum;
    }

    /** Baseline: unordered list, full scan for the maximum on every extract (priority recomputation). */
    static long runScan(Ops o, List<Integer> extracted) {
        int n = o.code.length;
        int[] ids = new int[n];
        double[] pr = new double[n];
        int[] pos = new int[n];
        java.util.Arrays.fill(pos, -1);
        int size = 0;
        long sum = 0;
        for (int i = 0; i < n; i++) {
            switch (o.code[i]) {
                case 0 -> {
                    ids[size] = o.id[i];
                    pr[size] = o.p[i];
                    pos[o.id[i]] = size++;
                }
                case 1 -> {
                    int best = 0;
                    for (int j = 1; j < size; j++)
                        if (pr[j] > pr[best] || (pr[j] == pr[best] && ids[j] < ids[best])) best = j;
                    int x = ids[best];
                    sum += x;
                    if (extracted != null) extracted.add(x);
                    pos[x] = -1;
                    size--;
                    if (best != size) {
                        ids[best] = ids[size];
                        pr[best] = pr[size];
                        pos[ids[best]] = best;
                    }
                }
                default -> {
                    int at = pos[o.id[i]];
                    if (at >= 0) pr[at] = o.p[i];
                }
            }
        }
        return sum;
    }

    public static List<BenchmarkHarness.Sample> heap(int[] sizes, int scanLimit, int warmup, int reps) {
        List<BenchmarkHarness.Sample> all = new ArrayList<>();
        for (int n : sizes) {
            Ops o = ops(n, 555L + n);
            List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
            runHeap(o, a);
            if (n <= scanLimit) {
                runScan(o, b);
                if (!a.equals(b)) throw new IllegalStateException("heap and scan extraction orders differ at n=" + n);
            }
            all.addAll(BenchmarkHarness.measure("heap", "indexed-heap", n, warmup, reps, () -> {
                return n + (runHeap(o, null) & 0);
            }));
            if (n <= scanLimit)
                all.addAll(BenchmarkHarness.measure("heap", "linear-scan", n, warmup, reps, () -> {
                    return n + (runScan(o, null) & 0);
                }));
        }
        return all;
    }

    // ---------------------------------------------------------------- TSP: Double-Tree vs NN vs exact

    /** Euclidean distances between random points in a 30 km square: a true metric. */
    static double[][] euclidean(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = r.nextDouble() * 30;
            y[i] = r.nextDouble() * 30;
        }
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) d[i][j] = Math.hypot(x[i] - x[j], y[i] - y[j]);
        return d;
    }

    public static List<BenchmarkHarness.Sample> tsp(int[] sizes, int warmup, int reps) {
        List<BenchmarkHarness.Sample> all = new ArrayList<>();
        for (int n : sizes) {
            double[][] d = euclidean(n, 3000L + n);
            var dt = DoubleTreeTsp.closedTour(d, 0);
            if (!dt.approximationGuaranteeApplies()) throw new IllegalStateException("metric check failed at n=" + n);
            if (dt.cost() > 2 * dt.mstCost() + 1e-9) throw new IllegalStateException("2-approx bound broken at n=" + n);
            for (long seed = 1; seed <= 10; seed++) {
                double[][] m = euclidean(n, 40_000L + 100L * n + seed);
                var t = DoubleTreeTsp.closedTour(m, 0);
                var nn = NearestNeighbourTsp.closedTour(m, 0);
                q("tsp", "double-tree", n, seed, "cost_over_mst", t.cost() / t.mstCost());
                q("tsp", "nearest-neighbour", n, seed, "cost_over_mst", nn.cost() / t.mstCost());
                if (n <= ExactTsp.MAX_N) {
                    double opt = ExactTsp.closedTour(m, 0).cost();
                    q("tsp", "double-tree", n, seed, "cost_over_optimum", t.cost() / opt);
                    q("tsp", "nearest-neighbour", n, seed, "cost_over_optimum", nn.cost() / opt);
                }
            }
            all.addAll(BenchmarkHarness.measure("tsp", "double-tree", n, warmup, reps, () -> {
                return 1 + (DoubleTreeTsp.closedTour(d, 0).tour().length & 0);
            }));
            // Double-Tree time includes the O(n^3) metric check; time the check alone to separate it.
            all.addAll(BenchmarkHarness.measure("tsp", "metric-validation-only", n, warmup, reps, () -> {
                return 1 + (MetricValidator.validate(d).triangleInequality() ? 0 : 0);
            }));
            all.addAll(BenchmarkHarness.measure("tsp", "nearest-neighbour", n, warmup, reps, () -> {
                return 1 + (NearestNeighbourTsp.closedTour(d, 0).tour().length & 0);
            }));
            if (n <= ExactTsp.MAX_N)
                all.addAll(BenchmarkHarness.measure(
                        "tsp", "exact-held-karp", n, Math.min(warmup, 2), Math.min(reps, 5), () -> {
                            return 1 + (ExactTsp.closedTour(d, 0).tour().length & 0);
                        }));
        }
        return all;
    }
}
