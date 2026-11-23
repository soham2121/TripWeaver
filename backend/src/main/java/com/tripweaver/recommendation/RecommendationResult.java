package com.tripweaver.recommendation;

import com.tripweaver.place.PlaceSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Selected places with explanations, coverage, the Quickselect pool and a ranking-only baseline. */
public record RecommendationResult(
        long seed,
        Pool pool,
        List<Pick> selected,
        Coverage coverage,
        Baseline rankingOnlyBaseline,
        List<Scored> fill,
        Map<String, Integer> excluded,
        List<String> warnings) {

    /** Quickselect stage: eligible count, K, score threshold, quota reservations and shortfalls. */
    public record Pool(
            int eligible,
            int k,
            double threshold,
            int partitionRounds,
            Map<String, Integer> reserved,
            Map<String, Integer> shortfalls) {}

    public record Pick(
            PlaceSummary place,
            ScoreBreakdown score,
            int step,
            List<String> newlyCovered,
            double marginalGain,
            double selectionCost,
            double ratio,
            boolean mustVisit,
            String reason) {}

    public record Coverage(
            List<String> covered,
            List<String> uncovered,
            double coveredWeight,
            double totalWeight,
            String stopReason,
            long gainEvaluations,
            long fullGreedyGainEvaluations,
            boolean lazyEqualsFull) {}

    /** Same number of places chosen by relevance score alone, for comparison. */
    public record Baseline(List<UUID> placeIds, List<String> covered, List<String> uncovered, double coveredWeight) {}

    public record Scored(PlaceSummary place, ScoreBreakdown score) {}
}
