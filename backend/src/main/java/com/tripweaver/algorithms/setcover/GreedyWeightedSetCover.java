package com.tripweaver.algorithms.setcover;

/**
 * Greedy weighted set cover with full recomputation (Unit 5). Each round evaluates every unused
 * candidate and picks max (uncovered weight covered) / cost; ties go to the smaller id.
 *
 * <p>Stops when covered weight reaches the target, the selection limit is hit, or no candidate adds
 * positive weight.
 *
 * <p>Guarantee boundary: with equal tag weights this is the classical greedy, whose cost for covering all
 * coverable tags is at most H(d)·OPT (d = largest candidate tag set). With unequal weights it is a
 * heuristic ordering. Neither statement extends to the full multi-day itinerary.
 *
 * <p>Complexity: O(r · Σ|S_p|) for r picks. This is also the reference for {@link LazyGreedySetCover}.
 */
public final class GreedyWeightedSetCover {

    private GreedyWeightedSetCover() {}

    public static SetCoverResult solve(SetCoverInstance inst, SetCoverOptions opts) {
        CoverState s = new CoverState(inst, opts);
        while (true) {
            if (s.targetReached()) return s.result(SetCoverResult.StopReason.TARGET_REACHED);
            if (s.picks >= opts.maxSelections()) return s.result(SetCoverResult.StopReason.SELECTION_LIMIT);
            int best = -1;
            double bestRatio = 0;
            for (int c = 0; c < s.cands.size(); c++) {
                if (s.used[c]) continue;
                double g = s.gain(c);
                if (g <= 0) continue;
                double r = s.ratio(c, g);
                if (best < 0 || r > bestRatio) {
                    best = c;
                    bestRatio = r;
                }
            }
            if (best < 0) return s.result(SetCoverResult.StopReason.NO_POSITIVE_GAIN);
            s.take(best, false);
        }
    }
}
