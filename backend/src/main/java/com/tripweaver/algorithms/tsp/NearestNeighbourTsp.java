package com.tripweaver.algorithms.tsp;

/** Baseline: from the base repeatedly go to the nearest unvisited stop (lower index on ties). O(n²). */
public final class NearestNeighbourTsp {

    private NearestNeighbourTsp() {}

    public static TourResult closedTour(double[][] d, int base) {
        int n = d.length;
        if (n == 0 || base < 0 || base >= n) throw new IllegalArgumentException("bad matrix or base");
        boolean[] seen = new boolean[n];
        int[] tour = new int[n + 1];
        tour[0] = base;
        seen[base] = true;
        for (int i = 1; i < n; i++) {
            int cur = tour[i - 1], best = -1;
            for (int v = 0; v < n; v++) if (!seen[v] && (best < 0 || d[cur][v] < d[cur][best])) best = v;
            tour[i] = best;
            seen[best] = true;
        }
        tour[n] = base;
        return new TourResult(tour, TourResult.cost(d, tour), Double.NaN, false, "nearest-neighbour");
    }
}
