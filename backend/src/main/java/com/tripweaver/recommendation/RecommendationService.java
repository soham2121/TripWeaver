package com.tripweaver.recommendation;

import com.tripweaver.common.AlgorithmRunLog;
import com.tripweaver.place.PlaceCatalogue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Runs the recommendation pipeline on the live catalogue and logs algorithm metadata. */
@Service
public class RecommendationService {

    private final PlaceCatalogue catalogue;
    private final AlgorithmRunLog runs;

    public RecommendationService(PlaceCatalogue catalogue, AlgorithmRunLog runs) {
        this.catalogue = catalogue;
        this.runs = runs;
    }

    public record Response(UUID runId, long durationMicros, RecommendationResult result) {}

    public Response recommend(RecommendationRequest req, UUID tripId, String context) {
        var places = catalogue.index(req.destination()).all();
        long t0 = System.nanoTime();
        RecommendationResult r = RecommendationEngine.recommend(places, req);
        long micros = (System.nanoTime() - t0) / 1000;

        Map<String, Object> qs = new LinkedHashMap<>();
        qs.put("k", r.pool().k());
        qs.put("threshold", Double.isNaN(r.pool().threshold()) ? null : r.pool().threshold());
        qs.put("partitionRounds", r.pool().partitionRounds());
        qs.put("reserved", r.pool().reserved());
        qs.put("shortfalls", r.pool().shortfalls());
        runs.record(
                context,
                tripId,
                "randomized-quickselect+category-quotas",
                r.pool().eligible(),
                r.seed(),
                null,
                qs);

        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("selected", r.selected().size());
        sc.put("covered", r.coverage().covered());
        sc.put("uncovered", r.coverage().uncovered());
        sc.put("stopReason", r.coverage().stopReason());
        sc.put("heapGainEvaluations", r.coverage().gainEvaluations());
        sc.put("fullGreedyGainEvaluations", r.coverage().fullGreedyGainEvaluations());
        sc.put("lazyEqualsFull", r.coverage().lazyEqualsFull());
        runs.record(
                context,
                tripId,
                "lazy-greedy-weighted-set-cover+binary-heap",
                r.pool().k(),
                null,
                null,
                sc);

        UUID runId = runs.record(
                context,
                tripId,
                "recommendation-pipeline",
                places.size(),
                r.seed(),
                micros,
                Map.of("selected", r.selected().size(), "excluded", r.excluded()));
        return new Response(runId, micros, r);
    }
}
