package com.tripweaver.roadtrip;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.common.ApiException;
import com.tripweaver.place.PlaceHours;
import com.tripweaver.place.PlaceSummary;
import com.tripweaver.place.Town;
import com.tripweaver.routing.EstimateTravel;
import com.tripweaver.routing.LatLon;
import com.tripweaver.routing.Route;
import com.tripweaver.routing.RoutingService;
import com.tripweaver.routing.TransportMode;
import com.tripweaver.routing.TravelMatrix;
import com.tripweaver.routing.TravelProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Road trips on a synthetic straight corridor (19.0N 73.0E → 15.5N 73.8E, ≈ 9 h by the estimate). */
class RoadTripPlannerTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    final List<PlaceSummary> places = new ArrayList<>();
    final List<Town> towns = new ArrayList<>();
    TravelProvider travel = EstimateTravel.INSTANCE;

    static double lat(double t) {
        return 19.0 - 3.5 * t;
    }

    static double lon(double t) {
        return 73.0 + 0.8 * t;
    }

    PlaceSummary add(String name, String category, String interest, double t, double dLon, boolean activity) {
        PlaceSummary p = new PlaceSummary(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                lat(t),
                lon(t) + dLon,
                category,
                Set.of(category),
                interest == null ? Set.of() : Set.of(interest),
                "r",
                "r",
                null,
                activity,
                activity ? 60 : null,
                activity ? 60 : null,
                "LOW",
                List.of(),
                false,
                "PARTIAL",
                List.of(),
                Set.of(),
                List.of());
        places.add(p);
        return p;
    }

    void hotels() {
        for (int i = 1; i <= 9; i++) add("Hotel " + i, "accommodation", null, i / 10.0, 0.005, false);
    }

    void attractions() {
        add("Fort A", "fort", "heritage", 0.25, 0.02, true);
        add("Temple B", "religious_site", "heritage", 0.55, 0.02, true);
        add("Beach C", "beach", "beach", 0.40, -0.03, true);
        add("Far Fort", "fort", "heritage", 0.50, 0.6, true); // ≈ 64 km off the route
    }

    static String wp(String name, double t) {
        return "{\"name\":\"" + name + "\",\"lat\":" + lat(t) + ",\"lon\":" + lon(t) + "}";
    }

    RoadTripRequest request(String json) {
        String body = "{\"origin\":" + wp("Origin", 0) + ",\"destination\":" + wp("Destination", 1)
                + ",\"departureDate\":\"2026-12-14\",\"departureTime\":\"07:00\"" + (json.isEmpty() ? "" : "," + json)
                + "}";
        return JSON.readValue(body, RoadTripRequest.class);
    }

    RoadTrip plan(String json) {
        return plan(request(json));
    }

    RoadTrip plan(RoadTripRequest req) {
        return RoadTripPlanner.plan(places, towns, id -> null, travel, req);
    }

    ApiException fails(String json) {
        return assertThrows(ApiException.class, () -> plan(json));
    }

    static List<RoadTrip.Item> items(RoadTrip r) {
        return r.days().stream().flatMap(d -> d.items().stream()).toList();
    }

    static List<String> names(RoadTrip r, String type) {
        return items(r).stream()
                .filter(i -> i.type().equals(type))
                .map(RoadTrip.Item::name)
                .toList();
    }

    /** Driving limits, rest rules and chronological order hold on every day. */
    static void assertDrivingRules(RoadTrip r, int maxDayMinutes, int maxContinuous) {
        for (RoadTrip.Day d : r.days()) {
            assertTrue(d.drivingMinutes() <= maxDayMinutes, "day " + d.day() + " drives " + d.drivingMinutes());
            assertTrue(d.longestContinuousDrivingMinutes() <= maxContinuous, "day " + d.day());
            LocalTime t = d.start();
            int cont = 0;
            for (RoadTrip.Item i : d.items()) {
                assertFalse(i.start().isBefore(t), "items overlap on day " + d.day() + ": " + i);
                t = i.end();
                cont = i.type().equals("DRIVE") ? cont + i.minutes() : 0;
                assertTrue(cont <= maxContinuous, "continuous driving " + cont);
            }
            assertFalse(d.end().isAfter(LocalTime.of(20, 0)), "day " + d.day() + " ends " + d.end());
        }
    }

    @Test
    void longDriveIsSplitAtASuggestedOvernightStopWithinLimits() {
        hotels();
        RoadTrip r = plan("\"maxDrivingHoursPerDay\":5,\"maxContinuousDrivingMinutes\":120");
        assertEquals(2, r.days().size());
        assertEquals(1, r.nights());
        RoadTrip.Overnight o = r.days().getFirst().overnight();
        assertEquals("SUGGESTED", o.kind());
        assertTrue(o.name().startsWith("Hotel"));
        assertEquals("PROVIDER_SOURCED", o.confidence());
        assertTrue(o.note().contains("unknown"));
        assertDrivingRules(r, 300, 120);
        assertTrue(names(r, "REST").size() >= 1);
        assertEquals(
                1, names(r, "MEAL").stream().filter(n -> n.startsWith("Lunch")).count() / 1);
        assertEquals("Destination", names(r, "ARRIVE").getFirst());
        assertTrue(r.disclaimer().contains("heuristic"));
    }

    void town(String name, double t, double dLon, int stays, int sights) {
        towns.add(new Town(name, "town", lat(t), lon(t) + dLon, List.of()));
        for (int i = 0; i < stays; i++)
            add(name + " Lodge " + i, "accommodation", null, t, dLon + 0.005 * (i + 1), false);
        for (int i = 0; i < sights; i++) add(name + " Fort " + i, "fort", "heritage", t, dLon - 0.003 * (i + 1), true);
    }

    @Test
    void townStayRatings() {
        assertEquals(5, RoadTripPlanner.detourRating(0));
        assertEquals(5, RoadTripPlanner.detourRating(14));
        assertEquals(4, RoadTripPlanner.detourRating(15));
        assertEquals(2, RoadTripPlanner.detourRating(59));
        assertEquals(1, RoadTripPlanner.detourRating(60));
        assertEquals(0, RoadTripPlanner.interestRating(0));
        assertEquals(1, RoadTripPlanner.interestRating(1));
        assertEquals(1, RoadTripPlanner.interestRating(4));
        assertEquals(2, RoadTripPlanner.interestRating(5));
        assertEquals(5, RoadTripPlanner.interestRating(20));
        assertEquals(5, RoadTripPlanner.interestRating(80));
    }

    @Test
    void drivingDaysStopAtATownNearMidwayWithThingsToDo() {
        town("Early", 0.2, 0.01, 2, 0);
        town("Midtown", 0.5, 0.01, 2, 1);
        town("Busy", 0.58, 0.01, 1, 20); // ≈ 50 min past the midpoint, but much more to do
        town("Late", 0.8, 0.01, 1, 0);
        town("Empty", 0.3, 0.0, 0, 3); // nowhere to stay: not a proper stop
        town("Far", 0.5, 0.6, 3, 3); // ≈ 64 km off the route
        // ≈ 9 h fits one day at a 12 h limit and a late day end; 2 driving days still stop near halfway.
        String split = "\"drivingDays\":2,\"maxDrivingHoursPerDay\":12,\"dayEnd\":\"23:00\"";
        RoadTrip r = plan(split);
        assertEquals(2, r.days().size());
        RoadTrip.Overnight o = r.days().getFirst().overnight();
        assertEquals("TOWN", o.kind());
        assertEquals("Busy", o.name(), r.stayOptions().toString());
        assertTrue(o.note().contains("Busy Lodge 0"), o.note());
        RoadTrip.NightStays n = r.stayOptions().getFirst();
        assertEquals(1, n.night());
        List<String> shown = n.options().stream().map(RoadTrip.TownOption::town).toList();
        assertEquals(List.of("Busy", "Midtown"), shown.subList(0, 2), shown.toString());
        assertTrue(shown.stream().noneMatch(x -> x.equals("Empty") || x.equals("Far")), shown.toString());
        RoadTrip.TownOption busy = n.options().getFirst();
        assertTrue(busy.chosen());
        assertEquals(20, busy.sights());
        assertEquals(1, busy.stays());
        assertEquals(3, busy.topSights().size());
        assertEquals(RoadTripPlanner.detourRating(busy.detourMinutes()), busy.detourRating());
        assertEquals(20, busy.interestMatches()); // no interests chosen: every heritage sight counts
        assertEquals(5, busy.interestRating());
        RoadTrip.TownOption beachOnly =
                plan(split + ",\"interests\":{\"beach\":5}").stayOptions().getFirst().options().stream()
                        .filter(x -> x.town().equals("Busy"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, beachOnly.interestRating());

        // The user's pick from the options replaces the top town.
        String mid = n.options().get(1).key();
        RoadTrip picked = plan(split + ",\"stayChoices\":{\"1\":\"" + mid + "\"}");
        assertEquals("Midtown", picked.days().getFirst().overnight().name());
        assertEquals(
                List.of("Midtown"),
                picked.stayOptions().getFirst().options().stream()
                        .filter(RoadTrip.TownOption::chosen)
                        .map(RoadTrip.TownOption::town)
                        .toList());

        assertTrue(plan("").stayOptions().isEmpty());
        assertEquals("BAD_DAYS", fails("\"drivingDays\":0").errorCode());
    }

    @Test
    void fixedWaypointsKeepTheirOrderEvenWhenZigZagging() {
        hotels();
        RoadTrip r = plan("\"waypoints\":[" + wp("South first", 0.7) + "," + wp("North second", 0.3) + "],"
                + "\"maxDrivingHoursPerDay\":8");
        assertEquals(List.of("South first", "North second"), names(r, "WAYPOINT"));
        assertEquals(3, r.segments().size());
        assertEquals("Origin", r.segments().get(0).from());
        assertEquals("South first", r.segments().get(0).to());
        assertEquals("North second", r.segments().get(1).to());
        assertEquals("Destination", r.segments().get(2).to());
        for (RoadTrip.Segment s : r.segments()) assertTrue(s.directMinutes() > 60, s.from() + " → " + s.to());
        assertTrue(r.totalDrivingMinutes()
                >= r.segments().stream()
                        .mapToInt(RoadTrip.Segment::directMinutes)
                        .sum());
    }

    @Test
    void userOvernightWaypointEndsTheDay() {
        RoadTrip r = plan("\"waypoints\":[{\"name\":\"Stay\",\"lat\":" + lat(0.45) + ",\"lon\":" + lon(0.45)
                + ",\"overnight\":true}],\"maxDrivingHoursPerDay\":6");
        assertEquals(2, r.days().size());
        RoadTrip.Overnight o = r.days().getFirst().overnight();
        assertEquals("USER", o.kind());
        assertEquals("Stay", o.name());
        assertEquals(LocalTime.of(8, 0), r.days().get(1).start());
        assertTrue(r.days().getFirst().drivingMinutes() > 120, "day 1 drives to the stay");
        assertTrue(r.days().get(1).drivingMinutes() > 120, "day 2 drives on");
    }

    @Test
    void detoursAreRankedOptionalAndBounded() {
        hotels();
        attractions();
        RoadTrip r = plan("\"interests\":{\"heritage\":5,\"beach\":3},\"maxDrivingHoursPerDay\":6,"
                + "\"maxDetourMinutes\":30,\"maxDetours\":2");
        List<RoadTrip.DetourStop> detours =
                r.segments().stream().flatMap(s -> s.detours().stream()).toList();
        assertFalse(detours.isEmpty());
        assertTrue(detours.size() <= 2);
        for (RoadTrip.DetourStop d : detours) {
            assertTrue(d.addedDriveMinutes() <= 30, d.toString());
            assertNotEquals("Far Fort", d.place().name());
            assertFalse(d.reason().isBlank());
        }
        assertEquals(detours.size(), names(r, "DETOUR").size());
        RoadTrip.Segment seg = r.segments().getFirst();
        assertEquals(
                seg.detours().size() >= 2 ? "double-tree-open-path" : "route-order",
                seg.ordering().method());
        assertFalse(r.segments().getFirst().ordering().guaranteeApplies());
        assertDrivingRules(r, 360, 120);
    }

    @Test
    void tightArrivalDropsDetoursBeforeFailing() {
        hotels();
        attractions();
        String base = "\"interests\":{\"heritage\":5,\"beach\":3},\"maxDrivingHoursPerDay\":12,"
                + "\"maxDetourMinutes\":40,\"dayEnd\":\"23:00\"";
        RoadTrip loose = plan(base);
        int withDetours = names(loose, "DETOUR").size();
        assertTrue(withDetours > 0);
        LocalDateTime noDetourArrival =
                plan("\"maxDrivingHoursPerDay\":12,\"dayEnd\":\"23:00\"").arrival();
        RoadTrip tight = plan(base + ",\"arriveBy\":\"" + noDetourArrival.plusMinutes(5) + "\"");
        assertTrue(names(tight, "DETOUR").size() < withDetours);
        assertFalse(tight.arrival().isAfter(noDetourArrival.plusMinutes(5)));
        assertTrue(
                tight.warnings().stream().anyMatch(w -> w.contains("dropped")),
                tight.warnings().toString());
    }

    @Test
    void aClosedDetourIsDroppedWithoutDroppingAnOpenOneOfLowerValue() {
        PlaceSummary closed = add("Sunday Fort", "fort", "heritage", 0.25, 0.02, true);
        add("Open Beach", "beach", "beach", 0.40, -0.03, true);
        // 2026-12-14 is a Monday; the fort opens only on Sundays.
        RoadTrip r = RoadTripPlanner.plan(
                places,
                towns,
                id -> id.equals(closed.id()) ? new PlaceHours("Su 10:00-17:00", "VERIFIED") : null,
                travel,
                request("\"interests\":{\"heritage\":5,\"beach\":1},\"maxDrivingHoursPerDay\":12,"
                        + "\"dayEnd\":\"23:00\",\"maxDetours\":2,\"maxDetourMinutes\":40"));
        assertEquals(List.of("Open Beach"), names(r, "DETOUR"), r.warnings().toString());
        assertTrue(
                r.warnings()
                        .contains("Detour Sunday Fort dropped: Detour Sunday Fort is closed when you would arrive."),
                r.warnings().toString());
    }

    @Test
    void aDroppedDetourIsReplacedByTheNextRankedOne() {
        PlaceSummary closed = add("Sunday Fort", "fort", "heritage", 0.25, 0.02, true);
        add("Open Beach", "beach", "beach", 0.40, -0.03, true);
        // Only one detour allowed: the fort takes it, is closed on Monday 2026-12-14, and frees the slot.
        RoadTrip r = RoadTripPlanner.plan(
                places,
                towns,
                id -> id.equals(closed.id()) ? new PlaceHours("Su 10:00-17:00", "VERIFIED") : null,
                travel,
                request("\"interests\":{\"heritage\":5,\"beach\":1},\"maxDrivingHoursPerDay\":12,"
                        + "\"dayEnd\":\"23:00\",\"maxDetours\":1,\"maxDetourMinutes\":40"));
        assertEquals(List.of("Open Beach"), names(r, "DETOUR"), r.warnings().toString());
    }

    @Test
    void impossibleArrivalTimeIsInfeasibleWithTheEstimate() {
        ApiException e = fails("\"arriveBy\":\"2026-12-14T10:00\",\"maxDrivingHoursPerDay\":12,\"dayEnd\":\"23:00\"");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.status());
        assertEquals("INFEASIBLE_PLAN", e.errorCode());
        assertTrue(e.details().get("conflicts").toString().contains("arrive"));
        assertNotNull(e.details().get("estimatedArrival"));
    }

    @Test
    void longDayWithoutAnyAccommodationIsAnImpossibleDrivingDay() {
        ApiException e = fails("\"maxDrivingHoursPerDay\":3");
        assertEquals("INFEASIBLE_PLAN", e.errorCode());
        assertTrue(
                e.details().get("conflicts").toString().contains("overnight"),
                e.details().toString());
        assertTrue(e.details().get("suggestedRelaxations").toString().contains("overnight waypoint"));
    }

    @Test
    void unreachableWaypointIsInfeasible() {
        travel = new TravelProvider() {
            @Override
            public TravelMatrix matrix(List<LatLon> pts, TransportMode mode) {
                return EstimateTravel.INSTANCE.matrix(pts, mode);
            }

            @Override
            public Route route(List<LatLon> w, TransportMode mode) {
                return w.getLast().lat() < 16
                        ? new Route(RoutingService.NO_ROUTE, "none", List.of(), List.of(), 0, 0, List.of())
                        : EstimateTravel.INSTANCE.route(w, mode);
            }
        };
        ApiException e = fails("\"waypoints\":[" + wp("Midway", 0.5) + "]");
        assertTrue(
                e.details().get("conflicts").toString().contains("No road"),
                e.details().toString());
    }

    @Test
    void routingOutageFallsBackToLabelledEstimates() {
        hotels();
        travel = new RoutingService("http://127.0.0.1:9", 300);
        RoadTrip r = plan("\"maxDrivingHoursPerDay\":6");
        assertEquals("ESTIMATE", r.travelProvider());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("unavailable")));
        assertFalse(r.days().isEmpty());
    }

    @Test
    void costsAreLabelledAndUnknownsStayUnknown() {
        hotels();
        RoadTrip r = plan(
                "\"maxDrivingHoursPerDay\":5,\"partySize\":3,\"mileageKmPerLitre\":15," + "\"fuelPricePerLitre\":101");
        var lines = r.budget().lines();
        var fuel = lines.stream()
                .filter(l -> l.category().equals("FUEL"))
                .findFirst()
                .orElseThrow();
        assertEquals("CURATED_ESTIMATE", fuel.confidence());
        assertEquals(r.totalKm() / 15 * 101, fuel.max(), 1);
        var tolls = lines.stream()
                .filter(l -> l.category().equals("TOLLS"))
                .findFirst()
                .orElseThrow();
        assertNull(tolls.max());
        assertEquals("UNKNOWN", tolls.confidence());
        var stay = lines.stream()
                .filter(l -> l.category().equals("ACCOMMODATION"))
                .findFirst()
                .orElseThrow();
        assertNull(stay.max());
        assertFalse(r.budget().complete());

        RoadTrip entered = plan("\"maxDrivingHoursPerDay\":5,\"tollsTotal\":850,\"accommodationPerNight\":3000");
        assertEquals(
                850.0,
                entered.budget().lines().stream()
                        .filter(l -> l.category().equals("TOLLS"))
                        .findFirst()
                        .orElseThrow()
                        .max());
        assertEquals(
                "USER_ENTERED",
                entered.budget().lines().stream()
                        .filter(l -> l.category().equals("ACCOMMODATION"))
                        .findFirst()
                        .orElseThrow()
                        .confidence());
    }

    @Test
    void eachDayHasChainedNavigationLinks() {
        hotels();
        RoadTrip r = plan("\"maxDrivingHoursPerDay\":5");
        for (RoadTrip.Day d : r.days()) assertFalse(d.navigationUrls().isEmpty());
        assertTrue(r.days().getFirst().navigationUrls().getFirst().contains("origin=19.000000,73.000000"));
        assertTrue(r.days().getLast().navigationUrls().getLast().contains("destination=15.500000,73.800000"));
        assertTrue(Duration.between(r.departure(), r.arrival()).toHours() >= 20, "arrives the next day");
    }

    @Test
    void invalidInputIsRejected() {
        assertEquals("BAD_TRANSPORT", fails("\"transport\":\"WALK\"").errorCode());
        assertEquals("BAD_DRIVING_LIMIT", fails("\"maxDrivingHoursPerDay\":0").errorCode());
        String noOrigin = "{\"destination\":" + wp("D", 1) + "}";
        ApiException e = assertThrows(
                ApiException.class,
                () -> RoadTripPlanner.plan(
                        places, id -> null, travel, JSON.readValue(noOrigin, RoadTripRequest.class)));
        assertEquals("BAD_WAYPOINT", e.errorCode());
    }

    @Test
    void severalDetoursInOneSegmentAreOrderedByDoubleTreeOpenPathWithoutAGuarantee() {
        add("Fort P", "fort", "heritage", 0.20, 0.02, true);
        add("Fort Q", "fort", "heritage", 0.30, -0.02, true);
        add("Fort R", "fort", "heritage", 0.35, 0.02, true);
        RoadTrip r = plan("\"interests\":{\"heritage\":5},\"maxDrivingHoursPerDay\":12,\"dayEnd\":\"23:00\","
                + "\"maxDetours\":3,\"maxDetourMinutes\":40");
        RoadTrip.Segment s = r.segments().getFirst();
        assertTrue(s.detours().size() >= 2, s.detours().toString());
        assertEquals("double-tree-open-path", s.ordering().method());
        assertNotNull(s.ordering().doubleTreeMinutes());
        assertFalse(s.ordering().guaranteeApplies());
        assertTrue(Set.of("ROUTE_ORDER", "DOUBLE_TREE").contains(s.ordering().applied()));
        assertEquals("Origin", s.from());
        assertEquals("Destination", s.to());
    }

    @Test
    void interestsWithoutADetourAreReported() {
        hotels();
        attractions();
        RoadTrip r = plan("\"interests\":{\"heritage\":5,\"beach\":3},\"maxDrivingHoursPerDay\":12,"
                + "\"dayEnd\":\"23:00\",\"maxDetours\":1");
        assertEquals(1, names(r, "DETOUR").size());
        assertTrue(
                r.warnings().stream().anyMatch(w -> w.contains("beach") && w.contains("not covered")),
                r.warnings().toString());
    }
}
