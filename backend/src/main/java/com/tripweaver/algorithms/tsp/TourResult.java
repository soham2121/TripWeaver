package com.tripweaver.algorithms.tsp;

/**
 * Visit order over matrix indices. Closed tours start and end at the base ({@code n + 1} entries); open
 * paths list each vertex once.
 *
 * @param mstCost MST weight (Double-Tree only, NaN otherwise); a lower bound on the optimal closed tour
 * @param approximationGuaranteeApplies true only for a closed Double-Tree tour on a validated metric
 */
public record TourResult(
        int[] tour, double cost, double mstCost, boolean approximationGuaranteeApplies, String method) {

    /** Sum of consecutive leg lengths along the given order. */
    public static double cost(double[][] d, int[] order) {
        double c = 0;
        for (int i = 0; i + 1 < order.length; i++) c += d[order[i]][order[i + 1]];
        return c;
    }
}
