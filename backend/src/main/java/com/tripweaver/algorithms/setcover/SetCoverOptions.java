package com.tripweaver.algorithms.setcover;

import java.util.Set;

/**
 * @param targetCoverage stop once covered weight ≥ target × total weight; in (0, 1]
 * @param maxSelections limit on greedy picks (preselected items do not count)
 * @param preselected candidate ids forced in first (must-visits), in id order
 */
public record SetCoverOptions(double targetCoverage, int maxSelections, Set<String> preselected) {

    public SetCoverOptions {
        if (!(targetCoverage > 0 && targetCoverage <= 1))
            throw new IllegalArgumentException("targetCoverage must be in (0, 1]");
        if (maxSelections < 0) throw new IllegalArgumentException("maxSelections must be >= 0");
        preselected = Set.copyOf(preselected);
    }

    public static SetCoverOptions defaults() {
        return new SetCoverOptions(1.0, Integer.MAX_VALUE, Set.of());
    }
}
