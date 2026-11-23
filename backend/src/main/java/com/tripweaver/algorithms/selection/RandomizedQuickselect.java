package com.tripweaver.algorithms.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;

/**
 * Randomized Quickselect for top-K by score (Unit 1 selection, Unit 6 randomization, CO5).
 *
 * <p>Order: higher score first; equal scores ordered by {@code tieBreak} ascending. This total order makes
 * the selected set independent of the seed; the seed only changes the pivots and therefore the work done.
 *
 * <p>Uses a random pivot and a three-way (Dijkstra) partition, so runs of equal keys are handled in one
 * pass. Works on a defensive copy; the caller's list is never modified.
 *
 * <p>Complexity: expected O(n) comparisons, worst case O(n²) (adversarial pivots, probability vanishing
 * with random pivots); plus O(k log k) to sort the K winners for output. Extra space O(n) for the copy;
 * partitioning itself is in place.
 */
public final class RandomizedQuickselect {

    public static final String TIE_POLICY = "score descending; equal scores by tie-break comparator ascending";

    private RandomizedQuickselect() {}

    public static <T> SelectionResult<T> selectTopK(
            List<T> items, ToDoubleFunction<? super T> score, Comparator<? super T> tieBreak, int k, long seed) {
        int n = items.size();
        if (k < 0 || k > n) throw new IllegalArgumentException("k must be in [0, " + n + "], was " + k);

        @SuppressWarnings("unchecked")
        T[] a = (T[]) items.toArray();
        double[] s = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = score.applyAsDouble(a[i]);
            if (!Double.isFinite(s[i])) throw new IllegalArgumentException("score must be finite at index " + i);
        }
        if (k == 0) return new SelectionResult<>(List.of(), Double.NaN, seed, TIE_POLICY, n, 0);

        SplittableRandom rng = new SplittableRandom(seed);
        int rounds = 0;
        int lo = 0, hi = n - 1, target = k - 1;
        while (lo < hi) {
            int p = lo + rng.nextInt(hi - lo + 1);
            T pivot = a[p];
            double ps = s[p];
            int lt = lo, i = lo, gt = hi;
            while (i <= gt) {
                int c = compare(s[i], a[i], ps, pivot, tieBreak);
                if (c < 0) swap(a, s, lt++, i++);
                else if (c > 0) swap(a, s, i, gt--);
                else i++;
            }
            rounds++;
            // [lo, lt) better than pivot, [lt, gt] equal, (gt, hi] worse
            if (target < lt) hi = lt - 1;
            else if (target > gt) lo = gt + 1;
            else break;
        }

        Integer[] idx = new Integer[k];
        for (int i = 0; i < k; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> compare(s[x], a[x], s[y], a[y], tieBreak));
        List<T> selected = new ArrayList<>(k);
        for (int i : idx) selected.add(a[i]);
        double threshold = s[idx[k - 1]];
        return new SelectionResult<>(List.copyOf(selected), threshold, seed, TIE_POLICY, n, rounds);
    }

    /** Negative when (sx, x) ranks before (sy, y). */
    private static <T> int compare(double sx, T x, double sy, T y, Comparator<? super T> tieBreak) {
        if (sx != sy) return sx > sy ? -1 : 1;
        return tieBreak.compare(x, y);
    }

    private static <T> void swap(T[] a, double[] s, int i, int j) {
        T t = a[i];
        a[i] = a[j];
        a[j] = t;
        double d = s[i];
        s[i] = s[j];
        s[j] = d;
    }
}
