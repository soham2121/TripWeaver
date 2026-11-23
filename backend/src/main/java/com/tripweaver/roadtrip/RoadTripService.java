package com.tripweaver.roadtrip;

import com.tripweaver.common.AlgorithmRunLog;
import com.tripweaver.place.PlaceCatalogue;
import com.tripweaver.routing.RoutingService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoadTripService {

    private final PlaceCatalogue catalogue;
    private final RoutingService routing;
    private final AlgorithmRunLog runs;

    public RoadTripService(PlaceCatalogue catalogue, RoutingService routing, AlgorithmRunLog runs) {
        this.catalogue = catalogue;
        this.routing = routing;
        this.runs = runs;
    }

    public record Response(UUID runId, long durationMicros, RoadTrip roadTrip) {}

    public Response plan(RoadTripRequest req) {
        long t0 = System.nanoTime();
        RoadTrip r = RoadTripPlanner.plan(catalogue.allPlaces(), catalogue::hours, routing, req);
        long micros = (System.nanoTime() - t0) / 1000;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("days", r.days().size());
        s.put("nights", r.nights());
        s.put("segments", r.segments().size());
        s.put("detourCandidates", r.detourCandidates());
        s.put("detours", r.segments().stream().mapToInt(g -> g.detours().size()).sum());
        s.put("totalDrivingMinutes", r.totalDrivingMinutes());
        s.put("totalKm", r.totalKm());
        s.put("travelProvider", r.travelProvider());
        UUID runId = runs.record(
                "road-trip", null, "road-trip-detours+day-split", r.detourCandidates(), r.seed(), micros, s);
        return new Response(runId, micros, r);
    }
}
