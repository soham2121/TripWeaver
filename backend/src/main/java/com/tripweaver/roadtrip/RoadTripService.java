package com.tripweaver.roadtrip;

import com.tripweaver.common.AlgorithmRunLog;
import com.tripweaver.common.ApiException;
import com.tripweaver.place.PlaceCatalogue;
import com.tripweaver.place.TownGazetteer;
import com.tripweaver.planning.ItineraryPlanner;
import com.tripweaver.routing.RoutingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoadTripService {

    private final PlaceCatalogue catalogue;
    private final RoutingService routing;
    private final AlgorithmRunLog runs;
    private final TownGazetteer towns;

    public RoadTripService(
            PlaceCatalogue catalogue, RoutingService routing, AlgorithmRunLog runs, TownGazetteer towns) {
        this.towns = towns;
        this.catalogue = catalogue;
        this.routing = routing;
        this.runs = runs;
    }

    public record Response(UUID runId, long durationMicros, RoadTrip roadTrip) {}

    /** The destination counts as covered when a catalogue place lies this close to it. */
    static final double STAY_DATA_KM = 20;

    public Response plan(RoadTripRequest req) {
        long t0 = System.nanoTime();
        RoadTrip r = RoadTripPlanner.plan(catalogue.allPlaces(), towns.all(), catalogue::hours, routing, req);
        if (req.stayDays() > 0) r = withStay(r, req);
        if (req.planReturn()) r = withReturn(r, req);
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
        s.put("stayDays", req.stayDays());
        s.put(
                "returnOptions",
                r.returnTrip() == null
                        ? 0
                        : r.returnTrip().options().stream()
                                .filter(o -> o.plan() != null)
                                .count());
        UUID runId = runs.record(
                "road-trip", null, "road-trip-detours+day-split", r.detourCandidates(), r.seed(), micros, s);
        return new Response(runId, micros, r);
    }

    /**
     * Plans the days at the destination with the itinerary planner, based there. A destination without place data,
     * or a stay the planner rejects, gives a stay with a note (and a trip warning), never a failed road trip.
     */
    RoadTrip withStay(RoadTrip r, RoadTripRequest req) {
        // The destination may be a catalogue place given by id only: take its coordinates from the arrival.
        var arrive = r.days().getLast().items().stream()
                .filter(i -> "ARRIVE".equals(i.type()))
                .findFirst()
                .orElseThrow();
        var d = req.destination();
        String name = d.name() != null && !d.name().isBlank() ? d.name() : arrive.name();
        var b = new RoadTripRequest.Waypoint(name, arrive.lat(), arrive.lon(), d.placeId(), null, null);
        var dest = catalogue.destinationNear(b.lat(), b.lon(), STAY_DATA_KM);
        if (dest.isEmpty())
            return r.withStay(StayPlanner.stay(
                    req,
                    r.arrival(),
                    null,
                    null,
                    "No place data near " + name + ": the stay is counted, but its days are not planned."));
        try {
            var it = ItineraryPlanner.plan(
                    catalogue.index(dest.get()).all(),
                    catalogue::hours,
                    routing,
                    StayPlanner.request(req, b, r.arrival(), dest.get(), r.seed()));
            return r.withStay(StayPlanner.stay(req, r.arrival(), dest.get(), it, null));
        } catch (ApiException e) {
            RoadTrip warned = r.withWarnings(List.of("Stay: " + e.getMessage()));
            return warned.withStay(StayPlanner.stay(req, r.arrival(), dest.get(), null, e.getMessage()));
        }
    }

    /** The four ways home, each planned like a road trip from the destination back to the origin. */
    RoadTrip withReturn(RoadTrip r, RoadTripRequest req) {
        RoadTrip.ReturnTrip rt = ReturnPlanner.plan(
                req,
                r,
                towns.all(),
                leg -> RoadTripPlanner.plan(catalogue.allPlaces(), towns.all(), catalogue::hours, routing, leg));
        if (rt.chosen() != req.returnChoice())
            r = r.withWarnings(List.of("Way home: option " + req.returnChoice()
                    + " could not be planned; showing option " + rt.chosen() + "."));
        return r.withReturn(rt);
    }
}
