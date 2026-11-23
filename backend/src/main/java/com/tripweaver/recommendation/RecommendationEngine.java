package com.tripweaver.recommendation;

import com.tripweaver.algorithms.selection.CategoryAwareSelection;
import com.tripweaver.algorithms.selection.CategoryAwareSelector;
import com.tripweaver.algorithms.setcover.GreedyWeightedSetCover;
import com.tripweaver.algorithms.setcover.LazyGreedySetCover;
import com.tripweaver.algorithms.setcover.SetCoverCandidate;
import com.tripweaver.algorithms.setcover.SetCoverInstance;
import com.tripweaver.algorithms.setcover.SetCoverOptions;
import com.tripweaver.algorithms.setcover.SetCoverResult;
import com.tripweaver.common.ApiException;
import com.tripweaver.common.Geo;
import com.tripweaver.place.PlaceSummary;
import com.tripweaver.place.ingest.OsmCategoryMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Candidate selection pipeline (plan section 6.1, steps 4–9):
 *
 * <ol>
 *   <li>Hard filters: avoid list, region restriction, curated seasonal closure, wheelchair=no when required.
 *   <li>Transparent relevance score ({@link ScoreBreakdown}); places matching no requested interest drop out.
 *   <li>Category-aware Randomized Quickselect prunes to a pool of K with per-interest quotas.
 *   <li>Lazy-greedy Weighted Set Cover (binary heap) picks a diverse set covering the weighted interests;
 *       must-visits are preselected. Full greedy runs alongside as a check.
 *   <li>Ranking-only baseline and remaining pool ("fill") are returned for comparison and scheduling.
 * </ol>
 *
 * Pure: no I/O. Heuristic; no global optimality is claimed for the trip.
 */
public final class RecommendationEngine {

    private RecommendationEngine() {}

    public static RecommendationResult recommend(List<PlaceSummary> catalogue, RecommendationRequest req) {
        Map<String, Double> weights = validate(req);
        Map<UUID, PlaceSummary> byId = new HashMap<>();
        for (PlaceSummary p : catalogue) byId.put(p.id(), p);
        checkMustVisits(req, byId);

        Map<String, Integer> excluded = new LinkedHashMap<>();
        for (String k : List.of("avoided", "outsideRegions", "seasonalClosure", "accessibility", "noInterestMatch"))
            excluded.put(k, 0);

        Map<UUID, ScoreBreakdown> scores = new HashMap<>();
        List<PlaceSummary> eligible = new ArrayList<>();
        for (PlaceSummary p : catalogue) {
            if (!p.activity() || req.mustVisit().contains(p.id())) continue;
            String reason = hardExclusion(p, req);
            if (reason != null) {
                excluded.merge(reason, 1, Integer::sum);
                continue;
            }
            ScoreBreakdown s = score(p, req, weights);
            if (s.interest() <= 0) {
                excluded.merge("noInterestMatch", 1, Integer::sum);
                continue;
            }
            scores.put(p.id(), s);
            eligible.add(p);
        }
        for (UUID m : req.mustVisit()) scores.put(m, score(byId.get(m), req, weights));

        // Quickselect with per-interest quotas.
        int k = Math.min(req.pool(), eligible.size());
        Map<String, Integer> quotas = new TreeMap<>();
        if (k > 0) for (String i : weights.keySet()) quotas.put(i, Math.min(req.quota(), k / weights.size()));
        CategoryAwareSelection<PlaceSummary> pool = CategoryAwareSelector.select(
                eligible,
                p -> scores.get(p.id()).total(),
                p -> interestsMatched(p, weights),
                Comparator.comparing(p -> p.id().toString()),
                quotas,
                k,
                req.seedOrDefault());
        Map<String, Integer> reserved = new TreeMap<>();
        pool.reserved().forEach((c, l) -> reserved.put(c, l.size()));
        double threshold = pool.selected().isEmpty()
                ? Double.NaN
                : scores.get(pool.selected().getLast().id()).total();

        // Set cover over pool ∪ must-visits.
        List<PlaceSummary> candidates = new ArrayList<>(pool.selected());
        req.mustVisit().stream().sorted().map(byId::get).forEach(candidates::add);
        Map<String, PlaceSummary> byKey = new HashMap<>();
        Map<String, Double> costs = new HashMap<>();
        List<SetCoverCandidate> scCands = new ArrayList<>();
        for (PlaceSummary p : candidates) {
            double c = selectionCost(p, scores.get(p.id()), req);
            byKey.put(p.id().toString(), p);
            costs.put(p.id().toString(), c);
            scCands.add(new SetCoverCandidate(p.id().toString(), interestsMatched(p, weights), c));
        }
        SetCoverInstance inst = new SetCoverInstance(weights, scCands);
        Set<String> pre = new HashSet<>();
        req.mustVisit().forEach(u -> pre.add(u.toString()));
        SetCoverOptions opts = new SetCoverOptions(1.0, req.selections(), pre);
        SetCoverResult lazy = LazyGreedySetCover.solve(inst, opts);
        SetCoverResult full = GreedyWeightedSetCover.solve(inst, opts);

        List<RecommendationResult.Pick> picks = new ArrayList<>();
        int step = 0;
        for (SetCoverResult.Step s : lazy.steps()) {
            PlaceSummary p = byKey.get(s.candidateId());
            List<String> newly = new ArrayList<>(new TreeSet<>(s.newlyCovered()));
            String reason = s.preselected()
                    ? "Must-visit" + (newly.isEmpty() ? "" : "; covers " + String.join(", ", newly))
                    : String.format(
                            "Best value for uncovered interests: covers %s (gain %.2f / cost %.2f)",
                            String.join(", ", newly), s.marginalGain(), s.cost());
            picks.add(new RecommendationResult.Pick(
                    p,
                    scores.get(p.id()),
                    ++step,
                    newly,
                    s.marginalGain(),
                    s.cost(),
                    s.ratio(),
                    s.preselected(),
                    reason));
        }
        Set<UUID> picked = new HashSet<>();
        picks.forEach(p -> picked.add(p.place().id()));

        // Ranking-only baseline: same number of places by score alone (must-visits included).
        List<PlaceSummary> ranked = new ArrayList<>(pool.selected());
        ranked.sort(Comparator.comparingDouble(
                        (PlaceSummary p) -> -scores.get(p.id()).total())
                .thenComparing(p -> p.id().toString()));
        List<PlaceSummary> base = new ArrayList<>();
        req.mustVisit().stream().sorted().map(byId::get).forEach(base::add);
        for (PlaceSummary p : ranked) if (base.size() < picks.size()) base.add(p);
        Set<String> baseCovered = new TreeSet<>();
        for (PlaceSummary p : base) baseCovered.addAll(interestsMatched(p, weights));
        double baseWeight = baseCovered.stream().mapToDouble(weights::get).sum();
        Set<String> baseUncovered = new TreeSet<>(weights.keySet());
        baseUncovered.removeAll(baseCovered);

        List<RecommendationResult.Scored> fill = ranked.stream()
                .filter(p -> !picked.contains(p.id()))
                .map(p -> new RecommendationResult.Scored(p, scores.get(p.id())))
                .toList();

        List<String> warnings = new ArrayList<>();
        for (String u : lazy.uncovered())
            warnings.add("No eligible place covers '" + u + "' under the current filters.");
        pool.shortfalls()
                .forEach((c, n) -> warnings.add("Only " + (quotas.get(c) - n) + " candidate(s) for '" + c + "' (wanted "
                        + quotas.get(c) + ")."));
        long unknownCost =
                picks.stream().filter(p -> p.place().costs().isEmpty()).count();
        if (unknownCost > 0)
            warnings.add(
                    unknownCost + " selected place(s) have unknown cost; budget totals will be marked incomplete.");
        if (req.requireWheelchair()) {
            long unknownAccess = picks.stream()
                    .filter(p -> p.place().accessibility().stream().noneMatch(a -> a.startsWith("wheelchair:")))
                    .count();
            warnings.add("Wheelchair requirement: excluded " + excluded.get("accessibility")
                    + " place(s) tagged wheelchair=no; " + unknownAccess
                    + " selected place(s) have unknown wheelchair access.");
        }

        return new RecommendationResult(
                req.seedOrDefault(),
                new RecommendationResult.Pool(
                        eligible.size(), k, threshold, pool.partitionRounds(), reserved, pool.shortfalls()),
                List.copyOf(picks),
                new RecommendationResult.Coverage(
                        List.copyOf(lazy.covered()),
                        List.copyOf(lazy.uncovered()),
                        lazy.coveredWeight(),
                        lazy.totalWeight(),
                        lazy.stopReason().name(),
                        lazy.gainEvaluations(),
                        full.gainEvaluations(),
                        lazy.steps().equals(full.steps())),
                new RecommendationResult.Baseline(
                        base.stream().map(PlaceSummary::id).toList(),
                        List.copyOf(baseCovered),
                        List.copyOf(baseUncovered),
                        baseWeight),
                fill,
                excluded,
                List.copyOf(warnings));
    }

    static Map<String, Double> validate(RecommendationRequest req) {
        if (req.interests().isEmpty()) throw ApiException.badRequest("NO_INTERESTS", "Choose at least one interest");
        Map<String, Double> w = new TreeMap<>();
        for (var e : req.interests().entrySet()) {
            if (!OsmCategoryMapper.INTERESTS.contains(e.getKey()))
                throw ApiException.badRequest("UNKNOWN_INTEREST", "Unknown interest: " + e.getKey());
            if (e.getValue() == null || !(e.getValue() > 0) || e.getValue() > 5)
                throw ApiException.badRequest("BAD_WEIGHT", "Interest weight must be in (0, 5]: " + e.getKey());
            w.put(e.getKey(), e.getValue());
        }
        if (req.pool() < 1 || req.pool() > 500) throw ApiException.badRequest("BAD_POOL", "poolSize must be 1..500");
        if (req.selections() < 1 || req.selections() > 100)
            throw ApiException.badRequest("BAD_SELECTIONS", "maxSelections must be 1..100");
        if (req.quota() < 0 || req.quota() > 50)
            throw ApiException.badRequest("BAD_QUOTA", "quotaPerInterest must be 0..50");
        if (req.travelMonth() != null && (req.travelMonth() < 1 || req.travelMonth() > 12))
            throw ApiException.badRequest("BAD_MONTH", "travelMonth must be 1..12");
        return w;
    }

    private static void checkMustVisits(RecommendationRequest req, Map<UUID, PlaceSummary> byId) {
        List<String> conflicts = new ArrayList<>(), relax = new ArrayList<>();
        for (UUID m : req.mustVisit()) {
            PlaceSummary p = byId.get(m);
            if (p == null || !p.activity())
                throw ApiException.badRequest("UNKNOWN_PLACE", "Must-visit place not found: " + m);
            if (req.avoid().contains(m)) {
                conflicts.add(p.name() + " is both a must-visit and on the avoid list.");
                relax.add("Remove " + p.name() + " from one of the two lists.");
            }
            if (closedIn(p, req.travelMonth())) {
                conflicts.add(
                        p.name() + " is treated as closed in month " + req.travelMonth() + " (seasonal closure).");
                relax.add("Travel in another month or drop " + p.name() + " from must-visits.");
            }
            if (req.requireWheelchair() && p.accessibility().contains("wheelchair:no")) {
                conflicts.add(p.name() + " is tagged as not wheelchair accessible.");
                relax.add("Drop " + p.name() + " or the wheelchair requirement.");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INFEASIBLE_PLAN",
                    "The hard constraints conflict.",
                    Map.of("conflicts", conflicts, "suggestedRelaxations", relax));
        }
    }

    static String hardExclusion(PlaceSummary p, RecommendationRequest req) {
        if (req.avoid().contains(p.id())) return "avoided";
        if (!req.regions().isEmpty() && !req.regions().contains(p.regionSlug())) return "outsideRegions";
        if (closedIn(p, req.travelMonth())) return "seasonalClosure";
        if (req.requireWheelchair() && p.accessibility().contains("wheelchair:no")) return "accessibility";
        return null;
    }

    static boolean closedIn(PlaceSummary p, Integer month) {
        return month != null && p.seasonalClosedMonths().contains(month);
    }

    static Set<String> interestsMatched(PlaceSummary p, Map<String, Double> weights) {
        Set<String> s = new TreeSet<>(p.interests());
        s.retainAll(weights.keySet());
        return s;
    }

    static ScoreBreakdown score(PlaceSummary p, RecommendationRequest req, Map<String, Double> weights) {
        List<String> notes = new ArrayList<>();
        double maxReq =
                weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double maxM = 0, sumM = 0;
        for (String i : interestsMatched(p, weights)) {
            double w = weights.get(i);
            maxM = Math.max(maxM, w);
            sumM += w;
        }
        double interest = Math.min(1, (maxM + 0.25 * (sumM - maxM)) / maxReq);

        Double km = null;
        double distance = 0.5;
        if (req.baseLat() != null && req.baseLon() != null) {
            km = Geo.haversineKm(req.baseLat(), req.baseLon(), p.lat(), p.lon());
            distance = 1 / (1 + km / 8);
        } else notes.add("no base location: neutral distance score");

        double budget;
        double knownMax = p.costs().stream()
                .mapToDouble(PlaceSummary.CostEstimate::max)
                .max()
                .orElse(Double.NaN);
        if (Double.isNaN(knownMax)) {
            budget = 0.5;
            notes.add("cost unknown");
        } else if (req.maxEntryCostPerPerson() == null || knownMax <= req.maxEntryCostPerPerson()) {
            budget = 1;
        } else {
            budget = req.maxEntryCostPerPerson() / knownMax;
            notes.add("cost above per-activity preference");
        }

        double duration = 1;
        if (req.dayMinutes() != null && p.visitMinutesMax() != null) {
            double room = 0.4 * req.dayMinutes();
            if (p.visitMinutesMax() > room) {
                duration = room / p.visitMinutesMax();
                notes.add("long visit for the available day");
            }
        }

        double confidence = "COMPLETE".equals(p.dataQuality())
                ? 1
                : 0.4 + (p.costs().isEmpty() ? 0 : 0.3) + (p.hasOpeningHours() ? 0.3 : 0);

        double total = ScoreBreakdown.W_INTEREST * interest
                + ScoreBreakdown.W_DISTANCE * distance
                + ScoreBreakdown.W_BUDGET * budget
                + ScoreBreakdown.W_DURATION * duration
                + ScoreBreakdown.W_CONFIDENCE * confidence;
        return new ScoreBreakdown(total, interest, distance, budget, duration, confidence, km, List.copyOf(notes));
    }

    /**
     * Positive selection cost for Set Cover: duration, spend and detour burden (each normalised), divided by
     * (0.5 + relevance) so preferred places are cheaper to pick. Unknown spend counts as a neutral 0.5.
     */
    static double selectionCost(PlaceSummary p, ScoreBreakdown s, RecommendationRequest req) {
        double mid = p.visitMinutesMin() == null ? 60 : (p.visitMinutesMin() + p.visitMinutesMax()) / 2.0;
        double dur = Math.min(mid / 240.0, 1);
        double knownMax = p.costs().stream()
                .mapToDouble(PlaceSummary.CostEstimate::max)
                .max()
                .orElse(Double.NaN);
        double ref = req.maxEntryCostPerPerson() == null ? 500 : Math.max(1, req.maxEntryCostPerPerson());
        double spend = Double.isNaN(knownMax) ? 0.5 : Math.min(knownMax / ref, 2);
        double detour = s.distanceKm() == null ? 0.5 : Math.min(s.distanceKm() / 15.0, 2);
        double raw = 0.2 + 0.4 * dur + 0.2 * spend + 0.2 * detour;
        return raw / (0.5 + s.total());
    }
}
