package com.tripweaver.algorithms.setcover;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Weighted set cover input: universe of interest tags with weights w_u &gt; 0, and candidate places
 * with positive costs. Validated on construction.
 */
public record SetCoverInstance(Map<String, Double> weights, List<SetCoverCandidate> candidates) {

    public SetCoverInstance {
        weights = Map.copyOf(weights);
        candidates = List.copyOf(candidates);
        normalised(weights); // validates tags and weights
        Set<String> ids = new HashSet<>();
        for (SetCoverCandidate c : candidates) {
            if (!(c.cost() > 0) || !Double.isFinite(c.cost()))
                throw new IllegalArgumentException("cost must be finite and > 0 for " + c.id());
            if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate candidate id " + c.id());
        }
    }

    public static String normaliseTag(String tag) {
        return tag == null ? "" : tag.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Universe keyed by normalised tag, sorted. */
    public Map<String, Double> normalisedWeights() {
        return normalised(weights);
    }

    private static Map<String, Double> normalised(Map<String, Double> weights) {
        Map<String, Double> out = new TreeMap<>();
        for (var e : weights.entrySet()) {
            String t = normaliseTag(e.getKey());
            double w = e.getValue();
            if (t.isEmpty()) throw new IllegalArgumentException("blank tag");
            if (!(w > 0) || !Double.isFinite(w))
                throw new IllegalArgumentException("weight must be finite and > 0 for " + t);
            if (out.put(t, w) != null) throw new IllegalArgumentException("duplicate tag after normalising: " + t);
        }
        return out;
    }
}
