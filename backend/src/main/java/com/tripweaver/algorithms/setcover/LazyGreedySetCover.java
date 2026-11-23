package com.tripweaver.algorithms.setcover;

import com.tripweaver.algorithms.heap.IndexedMaxHeap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Greedy weighted set cover accelerated by a binary max heap ("lazy greedy").
 *
 * <p>The heap stores, per candidate, a gain/cost ratio computed earlier. Covering more tags can only
 * lower a candidate's gain and its cost is fixed and positive, so a stored ratio is an upper bound on
 * its current ratio. Pop the top, recompute; if it still beats the next stored bound (ties: smaller id
 * wins, matching {@link GreedyWeightedSetCover}) accept it, otherwise push it back with the new ratio.
 *
 * <p>Produces exactly the same selection sequence as the full-recomputation greedy, usually with far
 * fewer gain evaluations. Worst case O(r · n · log n) heap work, same as or worse than full greedy only
 * on adversarial inputs.
 */
public final class LazyGreedySetCover {

    private LazyGreedySetCover() {}

    public static SetCoverResult solve(SetCoverInstance inst, SetCoverOptions opts) {
        CoverState s = new CoverState(inst, opts);
        Map<Integer, Double> initial = new LinkedHashMap<>();
        for (int c = 0; c < s.cands.size(); c++) {
            if (s.used[c]) continue;
            double g = s.gain(c);
            if (g > 0) initial.put(c, s.ratio(c, g));
        }
        IndexedMaxHeap<Integer> heap = IndexedMaxHeap.buildFrom(initial, Comparator.naturalOrder());

        while (true) {
            if (s.targetReached()) return s.result(SetCoverResult.StopReason.TARGET_REACHED);
            if (s.picks >= opts.maxSelections()) return s.result(SetCoverResult.StopReason.SELECTION_LIMIT);
            int accepted = -1;
            while (!heap.isEmpty()) {
                int c = heap.extractMax().id();
                double g = s.gain(c);
                if (g <= 0) continue; // gain never grows back: drop permanently
                double r = s.ratio(c, g);
                if (heap.isEmpty()) {
                    accepted = c;
                    break;
                }
                IndexedMaxHeap.Entry<Integer> next = heap.peekMax();
                if (r > next.priority() || (r == next.priority() && c < next.id())) {
                    accepted = c;
                    break;
                }
                heap.insert(c, r);
            }
            if (accepted < 0) return s.result(SetCoverResult.StopReason.NO_POSITIVE_GAIN);
            s.take(accepted, false);
        }
    }
}
