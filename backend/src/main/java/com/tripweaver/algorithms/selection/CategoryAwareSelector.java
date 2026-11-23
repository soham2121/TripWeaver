package com.tripweaver.algorithms.selection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Top-K selection with per-category minimum quotas, so a rare requested interest is not pruned by a single
 * global top-K.
 *
 * <p>Steps: (1) for each quota category in name order, count already-chosen items in that category and
 * quickselect the missing number from unchosen items of that category; (2) quickselect the remaining
 * slots from everything still unchosen; (3) sort the pool with the same order as
 * {@link RandomizedQuickselect}. One item can satisfy several quotas.
 *
 * <p>Complexity: expected O(c·n + k log k) for c quota categories.
 */
public final class CategoryAwareSelector {

    private CategoryAwareSelector() {}

    public static <T> CategoryAwareSelection<T> select(
            List<T> items,
            ToDoubleFunction<? super T> score,
            Function<? super T, Set<String>> categories,
            Comparator<? super T> tieBreak,
            Map<String, Integer> quotas,
            int k,
            long seed) {
        int n = items.size();
        if (k < 0 || k > n) throw new IllegalArgumentException("k must be in [0, " + n + "], was " + k);
        int quotaSum = 0;
        for (var e : quotas.entrySet()) {
            if (e.getValue() < 0) throw new IllegalArgumentException("quota must be >= 0: " + e.getKey());
            quotaSum += e.getValue();
        }
        if (quotaSum > k) throw new IllegalArgumentException("quotas sum " + quotaSum + " exceeds k " + k);

        boolean[] chosen = new boolean[n];
        List<Integer> chosenIdx = new ArrayList<>();
        Map<String, List<T>> reserved = new LinkedHashMap<>();
        Map<String, Integer> shortfalls = new LinkedHashMap<>();
        ToDoubleFunction<Integer> idxScore = i -> score.applyAsDouble(items.get(i));
        Comparator<Integer> idxTie = (x, y) -> tieBreak.compare(items.get(x), items.get(y));
        int rounds = 0;

        for (var e : new TreeMap<>(quotas).entrySet()) {
            String cat = e.getKey();
            int have = 0;
            for (int i : chosenIdx) if (categories.apply(items.get(i)).contains(cat)) have++;
            int need = e.getValue() - have;
            if (need <= 0) continue;
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < n; i++)
                if (!chosen[i] && categories.apply(items.get(i)).contains(cat)) pool.add(i);
            int take = Math.min(need, pool.size());
            if (take < need) shortfalls.put(cat, need - take);
            if (take == 0) continue;
            SelectionResult<Integer> r =
                    RandomizedQuickselect.selectTopK(pool, idxScore, idxTie, take, seed ^ cat.hashCode());
            rounds += r.partitionRounds();
            List<T> res = new ArrayList<>();
            for (int i : r.selected()) {
                chosen[i] = true;
                chosenIdx.add(i);
                res.add(items.get(i));
            }
            reserved.put(cat, List.copyOf(res));
        }

        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!chosen[i]) rest.add(i);
        SelectionResult<Integer> fill =
                RandomizedQuickselect.selectTopK(rest, idxScore, idxTie, k - chosenIdx.size(), seed);
        rounds += fill.partitionRounds();
        chosenIdx.addAll(fill.selected());

        chosenIdx.sort((x, y) -> {
            double sx = idxScore.applyAsDouble(x), sy = idxScore.applyAsDouble(y);
            if (sx != sy) return sx > sy ? -1 : 1;
            return idxTie.compare(x, y);
        });
        List<T> selected = chosenIdx.stream().map(items::get).toList();
        return new CategoryAwareSelection<>(selected, reserved, shortfalls, seed, rounds);
    }
}
