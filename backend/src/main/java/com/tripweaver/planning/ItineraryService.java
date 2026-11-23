package com.tripweaver.planning;

import com.tripweaver.common.AlgorithmRunLog;
import com.tripweaver.place.PlaceCatalogue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Plans an itinerary on the live catalogue and logs the run. */
@Service
public class ItineraryService {

    private final PlaceCatalogue catalogue;
    private final AlgorithmRunLog runs;

    public ItineraryService(PlaceCatalogue catalogue, AlgorithmRunLog runs) {
        this.catalogue = catalogue;
        this.runs = runs;
    }

    public record Response(UUID runId, long durationMicros, Itinerary itinerary) {}

    public Response generate(ItineraryRequest req) {
        var places = catalogue.index(req.destination()).all();
        long t0 = System.nanoTime();
        Itinerary it = ItineraryPlanner.plan(places, catalogue::hours, req);
        long micros = (System.nanoTime() - t0) / 1000;

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("days", it.days().size());
        s.put("stops", it.days().stream().mapToInt(d -> d.stops().size()).sum());
        s.put(
                "workload",
                it.days().stream()
                        .map(d -> Map.of(
                                "day", d.day(),
                                "pace", d.pace(),
                                "label", d.workload().label(),
                                "utilisation", d.workload().utilisation(),
                                "freeMinutes", d.workload().freeMinutes()))
                        .toList());
        s.put("unscheduled", it.unscheduled().size());
        s.put("repairs", it.stats().repairs());
        s.put("insertionEvaluations", it.stats().insertionEvaluations());
        s.put("uncovered", it.coverage().uncovered());
        s.put("budgetStatus", it.budget().status());
        UUID runId = runs.record(
                "itinerary",
                null,
                "greedy-day-insertion+bounded-repair",
                it.stats().candidates(),
                it.seed(),
                micros,
                s);
        return new Response(runId, micros, it);
    }
}
