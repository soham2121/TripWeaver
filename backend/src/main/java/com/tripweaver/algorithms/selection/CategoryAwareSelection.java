package com.tripweaver.algorithms.selection;

import java.util.List;
import java.util.Map;

/**
 * Output of {@link CategoryAwareSelector}.
 *
 * @param selected final pool, best first
 * @param reserved items taken specifically to satisfy each category quota
 * @param shortfalls categories whose quota could not be met, with the missing count
 * @param seed base seed
 * @param partitionRounds total partition passes across all quickselect calls
 */
public record CategoryAwareSelection<T>(
        List<T> selected,
        Map<String, List<T>> reserved,
        Map<String, Integer> shortfalls,
        long seed,
        int partitionRounds) {}
