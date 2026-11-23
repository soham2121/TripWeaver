package com.tripweaver.recommendation;

import java.util.List;

/**
 * Transparent relevance score in [0, 1]: total = Σ weight × component. All components are in [0, 1].
 *
 * <ul>
 *   <li>interest: (max matched weight + 0.25 × other matched weights) / max requested weight, capped at 1
 *   <li>distance: 1 / (1 + km / 8) from the base (straight line); 0.5 when no base is given
 *   <li>budget: 1 if known cost fits the per-activity preference, else preference / cost; 0.5 if unknown
 *   <li>duration: 1 if the visit fits in 40% of the day, else proportionally less
 *   <li>confidence: 1 for complete data, else 0.4 + 0.3 (cost known) + 0.3 (hours known)
 * </ul>
 */
public record ScoreBreakdown(
        double total,
        double interest,
        double distance,
        double budget,
        double duration,
        double confidence,
        Double distanceKm,
        List<String> notes) {

    public static final double W_INTEREST = 0.45;
    public static final double W_DISTANCE = 0.20;
    public static final double W_BUDGET = 0.10;
    public static final double W_DURATION = 0.10;
    public static final double W_CONFIDENCE = 0.15;
}
