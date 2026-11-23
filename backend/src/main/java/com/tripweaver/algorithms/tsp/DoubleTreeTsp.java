package com.tripweaver.algorithms.tsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Metric TSP Double-Tree approximation (Unit 5): MST (own Prim, O(n²)), pre-order walk from the base
 * (equivalent to doubling the tree and shortcutting repeated vertices), then return to the base.
 *
 * <p>2-approximation holds only for complete, finite, non-negative, symmetric matrices that satisfy the
 * triangle inequality. Matrices that cannot form an undirected tree (asymmetric, non-finite, negative)
 * are rejected. If only the triangle inequality fails the tour is still built but flagged as having no
 * guarantee. Ties (equal edge weights) go to the lower index; children are visited in index order.
 *
 * <p>Complexity: O(n²) time for Prim on a dense matrix plus O(n) walk; O(n) extra space. Validation adds
 * O(n³) for the triangle check.
 */
public final class DoubleTreeTsp {

    private DoubleTreeTsp() {}

    public static TourResult closedTour(double[][] d, int base) {
        MetricReport m = checkUsable(d, base);
        int n = d.length;
        int[] parent = prim(d, base);
        List<Integer> pre = preorder(parent, base, -1);
        int[] tour = new int[n + 1];
        for (int i = 0; i < n; i++) tour[i] = pre.get(i);
        tour[n] = base;
        return new TourResult(
                tour, TourResult.cost(d, tour), mstWeight(d, parent), m.triangleInequality(), "double-tree");
    }

    /**
     * Path heuristic with fixed, different endpoints: MST pre-order from {@code start} skipping {@code end},
     * then {@code end}. No approximation bound is claimed.
     */
    public static TourResult openPath(double[][] d, int start, int end) {
        checkUsable(d, start);
        if (end < 0 || end >= d.length) throw new IllegalArgumentException("end out of range");
        if (start == end) throw new IllegalArgumentException("open path needs different endpoints; use closedTour");
        int[] parent = prim(d, start);
        List<Integer> pre = preorder(parent, start, end);
        pre.add(end);
        int[] path = pre.stream().mapToInt(Integer::intValue).toArray();
        return new TourResult(path, TourResult.cost(d, path), mstWeight(d, parent), false, "double-tree-open-path");
    }

    private static MetricReport checkUsable(double[][] d, int base) {
        if (d.length == 0) throw new IllegalArgumentException("empty matrix");
        MetricReport m = MetricValidator.validate(d);
        if (!m.usableForTree()) throw new IllegalArgumentException("distance matrix unusable: " + m.violations());
        if (base < 0 || base >= d.length) throw new IllegalArgumentException("base out of range");
        return m;
    }

    /** Prim's algorithm on a dense matrix rooted at {@code root}. Returns parent array (root → -1). */
    static int[] prim(double[][] d, int root) {
        int n = d.length;
        double[] key = new double[n];
        int[] parent = new int[n];
        boolean[] in = new boolean[n];
        java.util.Arrays.fill(key, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(parent, -1);
        key[root] = 0;
        for (int it = 0; it < n; it++) {
            int u = -1;
            for (int v = 0; v < n; v++) if (!in[v] && (u < 0 || key[v] < key[u])) u = v;
            in[u] = true;
            for (int v = 0; v < n; v++) {
                if (!in[v] && d[u][v] < key[v]) {
                    key[v] = d[u][v];
                    parent[v] = u;
                }
            }
        }
        return parent;
    }

    private static double mstWeight(double[][] d, int[] parent) {
        double w = 0;
        for (int v = 0; v < parent.length; v++) if (parent[v] >= 0) w += d[parent[v]][v];
        return w;
    }

    private static List<Integer> preorder(int[] parent, int root, int skip) {
        int n = parent.length;
        List<List<Integer>> children = new ArrayList<>(n);
        for (int i = 0; i < n; i++) children.add(new ArrayList<>());
        for (int v = 0; v < n; v++) if (parent[v] >= 0) children.get(parent[v]).add(v); // ascending index
        List<Integer> out = new ArrayList<>(n);
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            if (u != skip) out.add(u);
            List<Integer> ch = children.get(u);
            for (int i = ch.size() - 1; i >= 0; i--) stack.push(ch.get(i));
        }
        return out;
    }
}
