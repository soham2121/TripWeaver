package com.tripweaver.algorithms.setcover;

import java.util.List;
import java.util.Set;

/**
 * Selection sequence and coverage summary.
 *
 * @param gainEvaluations number of marginal-gain computations (work measure for heap vs full greedy)
 */
public record SetCoverResult(
        List<Step> steps,
        Set<String> covered,
        Set<String> uncovered,
        double coveredWeight,
        double totalWeight,
        double totalCost,
        StopReason stopReason,
        long gainEvaluations) {

    public enum StopReason {
        TARGET_REACHED,
        NO_POSITIVE_GAIN,
        SELECTION_LIMIT
    }

    /** One pick: which uncovered tags it added, their weight (gain), its cost and gain/cost ratio. */
    public record Step(
            String candidateId,
            Set<String> newlyCovered,
            double marginalGain,
            double cost,
            double ratio,
            boolean preselected) {}
}
