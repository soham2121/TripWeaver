package com.tripweaver.algorithms.setcover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exhaustive minimum-cost cover of every coverable universe tag. Reference for small instances only
 * (n ≤ 20 candidates, ≤ 63 tags). O(2^n · n).
 */
public final class ExactSetCover {

    public record Solution(List<String> ids, double cost) {}

    private ExactSetCover() {}

    public static Solution minCostCover(SetCoverInstance inst) {
        List<SetCoverCandidate> cs = inst.candidates();
        int n = cs.size();
        if (n > 20) throw new IllegalArgumentException("exact solver limited to 20 candidates");
        Map<String, Integer> idx = new HashMap<>();
        for (String t : inst.normalisedWeights().keySet()) idx.put(t, idx.size());
        if (idx.size() > 63) throw new IllegalArgumentException("exact solver limited to 63 tags");

        long[] mask = new long[n];
        long coverable = 0;
        for (int i = 0; i < n; i++) {
            for (String t : cs.get(i).normalisedTags()) {
                Integer b = idx.get(t);
                if (b != null) mask[i] |= 1L << b;
            }
            coverable |= mask[i];
        }
        double best = Double.POSITIVE_INFINITY;
        int bestSet = 0;
        for (int s = 0; s < (1 << n); s++) {
            long m = 0;
            double cost = 0;
            for (int i = 0; i < n; i++) {
                if ((s & (1 << i)) != 0) {
                    m |= mask[i];
                    cost += cs.get(i).cost();
                }
            }
            if (m == coverable && cost < best) {
                best = cost;
                bestSet = s;
            }
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++)
            if ((bestSet & (1 << i)) != 0) ids.add(cs.get(i).id());
        return new Solution(ids, best);
    }
}
