package com.tripweaver.algorithms.setcover;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared bookkeeping for the full and lazy greedy solvers so both compute gains identically (same tag
 * order, same floating-point sums) and produce identical outputs.
 */
final class CoverState {

    final String[] tagNames;
    final double[] tagWeight;
    final List<SetCoverCandidate> cands; // sorted by id; index order == id order
    final int[][] candTags; // universe tag indices per candidate, ascending
    final boolean[] covered;
    final boolean[] used;
    final double totalWeight;
    final double target;
    final List<SetCoverResult.Step> steps = new ArrayList<>();
    double coveredWeight;
    double totalCost;
    long evaluations;
    int picks;

    CoverState(SetCoverInstance inst, SetCoverOptions opts) {
        Map<String, Double> w = inst.normalisedWeights();
        tagNames = w.keySet().toArray(String[]::new);
        tagWeight = new double[tagNames.length];
        Map<String, Integer> tagIdx = new HashMap<>();
        double total = 0;
        for (int i = 0; i < tagNames.length; i++) {
            tagWeight[i] = w.get(tagNames[i]);
            tagIdx.put(tagNames[i], i);
            total += tagWeight[i];
        }
        totalWeight = total;
        target = opts.targetCoverage() * total;
        cands = new ArrayList<>(inst.candidates());
        cands.sort(Comparator.comparing(SetCoverCandidate::id));
        candTags = new int[cands.size()][];
        for (int c = 0; c < cands.size(); c++) {
            candTags[c] = cands.get(c).normalisedTags().stream()
                    .filter(tagIdx::containsKey)
                    .mapToInt(tagIdx::get)
                    .sorted()
                    .toArray();
        }
        covered = new boolean[tagNames.length];
        used = new boolean[cands.size()];

        Set<String> pre = new TreeSet<>(opts.preselected());
        for (String id : pre) {
            int c = indexOf(id);
            if (c < 0) throw new IllegalArgumentException("preselected id not among candidates: " + id);
            take(c, true);
        }
    }

    private int indexOf(String id) {
        for (int c = 0; c < cands.size(); c++) if (cands.get(c).id().equals(id)) return c;
        return -1;
    }

    double gain(int c) {
        evaluations++;
        double g = 0;
        for (int t : candTags[c]) if (!covered[t]) g += tagWeight[t];
        return g;
    }

    double ratio(int c, double gain) {
        return gain / cands.get(c).cost();
    }

    boolean targetReached() {
        return coveredWeight >= target - 1e-9;
    }

    void take(int c, boolean preselected) {
        Set<String> newly = new LinkedHashSet<>();
        double g = 0;
        for (int t : candTags[c]) {
            if (!covered[t]) {
                g += tagWeight[t];
                covered[t] = true;
                newly.add(tagNames[t]);
            }
        }
        double cost = cands.get(c).cost();
        used[c] = true;
        coveredWeight += g;
        totalCost += cost;
        if (!preselected) picks++;
        steps.add(new SetCoverResult.Step(cands.get(c).id(), Set.copyOf(newly), g, cost, g / cost, preselected));
    }

    SetCoverResult result(SetCoverResult.StopReason reason) {
        Set<String> cov = new TreeSet<>(), unc = new TreeSet<>();
        for (int t = 0; t < tagNames.length; t++) (covered[t] ? cov : unc).add(tagNames[t]);
        return new SetCoverResult(
                List.copyOf(steps), cov, unc, coveredWeight, totalWeight, totalCost, reason, evaluations);
    }
}
