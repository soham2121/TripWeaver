package com.tripweaver.algorithms.tsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks square shape, finiteness, non-negativity, zero diagonal, symmetry and the triangle inequality.
 * Floating comparisons use a relative tolerance of 1e-9. Triangle check is O(n³). Reports up to 5
 * concrete violations.
 */
public final class MetricValidator {

    private static final int MAX_REPORTED = 5;

    private MetricValidator() {}

    public static MetricReport validate(double[][] d) {
        List<String> v = new ArrayList<>();
        int n = d.length;
        for (double[] row : d) {
            if (row == null || row.length != n) {
                v.add("matrix is not square");
                return new MetricReport(false, false, false, false, false, false, v);
            }
        }
        boolean finite = true, nonNeg = true, zeroDiag = true, sym = true, tri = true;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double x = d[i][j];
                if (!Double.isFinite(x)) {
                    finite = false;
                    add(v, "d[" + i + "][" + j + "] is not finite");
                } else if (x < 0) {
                    nonNeg = false;
                    add(v, "d[" + i + "][" + j + "] is negative");
                }
            }
            if (d[i][i] != 0) {
                zeroDiag = false;
                add(v, "d[" + i + "][" + i + "] is not zero");
            }
        }
        if (!finite) return new MetricReport(true, false, nonNeg, zeroDiag, false, false, v);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!close(d[i][j], d[j][i])) {
                    sym = false;
                    add(v, "d[" + i + "][" + j + "] != d[" + j + "][" + i + "]");
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    double via = d[i][j] + d[j][k];
                    if (d[i][k] > via + 1e-9 * Math.max(1, via)) {
                        tri = false;
                        add(v, "d[" + i + "][" + k + "] > d[" + i + "][" + j + "] + d[" + j + "][" + k + "]");
                    }
                }
            }
        }
        return new MetricReport(true, true, nonNeg, zeroDiag, sym, tri, v);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }

    private static void add(List<String> v, String s) {
        if (v.size() < MAX_REPORTED) v.add(s);
    }
}
