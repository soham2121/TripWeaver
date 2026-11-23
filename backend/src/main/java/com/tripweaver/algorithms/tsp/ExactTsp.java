package com.tripweaver.algorithms.tsp;

import java.util.Arrays;

/**
 * Exact closed tour by Held–Karp dynamic programming. Reference for small instances only (n ≤ 16).
 * O(2^n · n²) time, O(2^n · n) space.
 */
public final class ExactTsp {

    public static final int MAX_N = 16;

    private ExactTsp() {}

    public static TourResult closedTour(double[][] d, int base) {
        int n = d.length;
        if (n == 0 || n > MAX_N) throw new IllegalArgumentException("exact TSP supports 1.." + MAX_N + " stops");
        if (base < 0 || base >= n) throw new IllegalArgumentException("base out of range");
        if (n == 1) return new TourResult(new int[] {base, base}, 0, Double.NaN, false, "held-karp");

        // Re-index so the base is vertex 0 of the DP; others are 1..n-1.
        int[] map = new int[n];
        map[0] = base;
        for (int v = 0, k = 1; v < n; v++) if (v != base) map[k++] = v;
        int m = n - 1;
        int full = 1 << m;
        double[][] dp = new double[full][m];
        int[][] prev = new int[full][m];
        for (double[] row : dp) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (int j = 0; j < m; j++) dp[1 << j][j] = d[map[0]][map[j + 1]];
        for (int s = 1; s < full; s++) {
            for (int j = 0; j < m; j++) {
                if ((s & (1 << j)) == 0 || dp[s][j] == Double.POSITIVE_INFINITY) continue;
                for (int k = 0; k < m; k++) {
                    if ((s & (1 << k)) != 0) continue;
                    int t = s | (1 << k);
                    double c = dp[s][j] + d[map[j + 1]][map[k + 1]];
                    if (c < dp[t][k]) {
                        dp[t][k] = c;
                        prev[t][k] = j;
                    }
                }
            }
        }
        double best = Double.POSITIVE_INFINITY;
        int last = -1;
        for (int j = 0; j < m; j++) {
            double c = dp[full - 1][j] + d[map[j + 1]][map[0]];
            if (c < best) {
                best = c;
                last = j;
            }
        }
        int[] tour = new int[n + 1];
        tour[0] = base;
        tour[n] = base;
        int s = full - 1;
        for (int pos = n - 1; pos >= 1; pos--) {
            tour[pos] = map[last + 1];
            int p = prev[s][last];
            s &= ~(1 << last);
            last = p;
        }
        return new TourResult(tour, best, Double.NaN, false, "held-karp");
    }
}
