package com.tripweaver.planning;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.common.ApiException;
import com.tripweaver.place.PlaceHours;
import com.tripweaver.place.PlaceSummary;
import com.tripweaver.routing.EstimateTravel;
import com.tripweaver.routing.LatLon;
import com.tripweaver.routing.Route;
import com.tripweaver.routing.RoutingService;
import com.tripweaver.routing.TransportMode;
import com.tripweaver.routing.TravelMatrix;
import com.tripweaver.routing.TravelProvider;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Day allocation, timelines, pace, budget and repair on small synthetic catalogues. */
class ItineraryPlannerTest {

    static final double BLAT = 15.50, BLON = 73.80;
    static final JsonMapper JSON = JsonMapper.builder().build();
    // 2026-11-23 is a Monday; 2026-11-27 a Friday.
    static final String MONDAY = "2026-11-23", FRIDAY = "2026-11-27";

    final List<PlaceSummary> places = new ArrayList<>();
    final Map<UUID, PlaceHours> hours = new HashMap<>();
    TravelProvider travel = EstimateTravel.INSTANCE;

    static UUID id(String n) {
        return UUID.nameUUIDFromBytes(n.getBytes());
    }

    PlaceSummary add(String name, String interest, double dLat, double dLon, int vMin, int vMax) {
        return add(name, interest, dLat, dLon, vMin, vMax, "LOW", List.of(), "north");
    }

    PlaceSummary add(
            String name,
            String interest,
            double dLat,
            double dLon,
            int vMin,
            int vMax,
            String intensity,
            List<PlaceSummary.CostEstimate> costs,
            String region) {
        PlaceSummary p = new PlaceSummary(
                id(name),
                name,
                BLAT + dLat,
                BLON + dLon,
                interest.equals("food") ? "restaurant" : interest.equals("nightlife") ? "bar" : interest,
                Set.of(interest),
                Set.of(interest),
                region,
                region,
                null,
                true,
                vMin,
                vMax,
                intensity,
                costs,
                false,
                "PARTIAL",
                List.of(),
                Set.of(),
                List.of());
        places.add(p);
        return p;
    }

    static PlaceSummary.CostEstimate perPerson(double amount) {
        return new PlaceSummary.CostEstimate("ENTRY", amount, amount, "INR", "PER_PERSON", "VERIFIED");
    }

    static PlaceSummary.CostEstimate perGroup(double amount) {
        return new PlaceSummary.CostEstimate("ACTIVITY", amount, amount, "INR", "PER_GROUP", "CURATED_ESTIMATE");
    }

    void hoursFor(PlaceSummary p, String raw) {
        hours.put(p.id(), new PlaceHours(raw, "VERIFIED"));
    }

    void cluster(int n, String interest, double dLat) {
        for (int i = 0; i < n; i++) add(interest + " " + i, interest, dLat + i * 0.002, 0.004 * (i % 3), 60, 90);
    }

    Itinerary plan(String json) {
        String body = "{\"baseLat\":" + BLAT + ",\"baseLon\":" + BLON + "," + json + "}";
        return ItineraryPlanner.plan(places, hours::get, travel, JSON.readValue(body, ItineraryRequest.class));
    }

    ApiException fails(String json) {
        return assertThrows(ApiException.class, () -> plan(json));
    }

    static List<Itinerary.Stop> allStops(Itinerary it) {
        return it.days().stream().flatMap(d -> d.stops().stream()).toList();
    }

    static Set<String> names(Itinerary it) {
        return allStops(it).stream().map(s -> s.place().name()).collect(Collectors.toSet());
    }

    /** Every day timeline is sequential and inside its window; every place appears once. */
    static void assertTimelinesFit(Itinerary it) {
        Set<UUID> seen = new HashSet<>();
        for (Itinerary.Day d : it.days()) {
            LocalTime t = d.start();
            for (Itinerary.Stop s : d.stops()) {
                assertTrue(seen.add(s.place().id()), "duplicate " + s.place().name());
                assertFalse(s.arrive().isBefore(t), "arrives before previous end");
                assertFalse(s.start().isBefore(s.arrive()));
                assertEquals(s.start().plusMinutes(s.visitMinutes()), s.end());
                for (Itinerary.Meal m : d.meals())
                    if (m.place() == null)
                        assertTrue(!m.start().isBefore(s.end()) || !m.end().isAfter(s.start()), "meal overlaps visit");
                t = s.end();
            }
            assertFalse(d.endOfDay().isAfter(d.end()), "day " + d.day() + " ends after its window");
        }
    }

    @Test
    void feasibleUnderBudgetPlanCoversInterestsAndFits() {
        cluster(6, "beach", 0.02);
        cluster(6, "food", 0.01);
        cluster(3, "heritage", -0.02);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"partySize\":2,"
                + "\"interests\":{\"beach\":5,\"food\":4,\"heritage\":3},"
                + "\"budget\":{\"amount\":20000,\"basis\":\"TOTAL\",\"strictness\":\"HARD\"}");
        assertEquals(2, it.days().size());
        assertTrue(it.coverage().uncovered().isEmpty(), it.coverage().toString());
        assertTrue(it.days().stream().allMatch(d -> !d.stops().isEmpty()));
        assertTimelinesFit(it);
        assertNotEquals("OVER_BUDGET", it.budget().status());
        assertTrue(it.disclaimer().contains("heuristic"));
    }

    @Test
    void hardBudgetLimitsOptionalStopsAndUnknownFoodIsReported() {
        for (int i = 0; i < 8; i++)
            add("Paid " + i, "culture", 0.01 * i, 0, 60, 60, "LOW", List.of(perPerson(300)), "north");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"partySize\":2,"
                + "\"interests\":{\"culture\":5},"
                + "\"budget\":{\"amount\":1500,\"basis\":\"TOTAL\",\"strictness\":\"HARD\"}");
        assertTrue(it.budget().knownMax() <= 1500, "known max " + it.budget().knownMax());
        assertEquals(2, allStops(it).size()); // 2 × (300 × 2 people) = 1200; a third would be 1800
        assertTrue(it.warnings().stream().anyMatch(w -> w.contains("left out to stay within the hard budget")));
        assertFalse(it.budget().complete()); // food and accommodation unknown
        assertTrue(it.budget().unknownItems().stream().anyMatch(s -> s.contains("Food")));
    }

    @Test
    void mustVisitsOverHardBudgetAreInfeasible() {
        PlaceSummary p = add("Pricey", "adventure", 0.01, 0, 60, 60, "HIGH", List.of(perPerson(2000)), "north");
        ApiException e = fails("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"adventure\":5},"
                + "\"mustVisit\":[\"" + p.id() + "\"],"
                + "\"budget\":{\"amount\":1000,\"basis\":\"PER_PERSON\",\"strictness\":\"HARD\"}");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.status());
        assertEquals("BUDGET_INFEASIBLE", e.errorCode());
        assertEquals(2000.0, e.details().get("estimatedMaximum"));
        assertEquals(1000.0, e.details().get("budget"));
    }

    @Test
    void flexibleBudgetOverrunIsAWarningNotARejection() {
        PlaceSummary p = add("Pricey", "adventure", 0.01, 0, 60, 60, "HIGH", List.of(perPerson(2000)), "north");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"adventure\":5},"
                + "\"mustVisit\":[\"" + p.id() + "\"],"
                + "\"budget\":{\"amount\":1000,\"basis\":\"PER_PERSON\",\"strictness\":\"FLEXIBLE\"}");
        assertEquals("OVER_BUDGET", it.budget().status());
        assertTrue(
                it.warnings().stream().anyMatch(w -> w.contains("exceeds")),
                it.warnings().toString());
    }

    @Test
    void unknownCostIsNeverZero() {
        cluster(3, "beach", 0.01);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},"
                + "\"budget\":{\"amount\":100,\"basis\":\"TOTAL\",\"strictness\":\"HARD\"}");
        assertFalse(allStops(it).isEmpty());
        for (Itinerary.Stop s : allStops(it)) {
            assertTrue(s.cost().unknown());
            assertNull(s.cost().max());
        }
        assertEquals("INCOMPLETE", it.budget().status());
        assertFalse(it.budget().complete());
        assertTrue(it.budget().unknownItems().size() >= allStops(it).size());
        assertTrue(it.budget().lines().stream()
                .filter(l -> l.category().equals("FOOD"))
                .allMatch(l -> l.max() == null && l.confidence().equals("UNKNOWN")));
    }

    @Test
    void partySizeAndCostBasisConvertCosts() {
        PlaceSummary a = add("Museum", "culture", 0.01, 0, 60, 60, "LOW", List.of(perPerson(100)), "north");
        PlaceSummary b = add("Boat", "water_sports", 0.012, 0, 60, 60, "LOW", List.of(perGroup(500)), "north");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"partySize\":4,"
                + "\"interests\":{\"culture\":5,\"water_sports\":5},"
                + "\"costs\":{\"foodPerPersonPerDay\":500,\"accommodationPerNight\":3000,"
                + "\"fuelPricePerLitre\":100,\"mileageKmPerLitre\":15}");
        Map<String, Itinerary.Stop> byName = new HashMap<>();
        allStops(it).forEach(s -> byName.put(s.place().name(), s));
        assertEquals(400.0, byName.get("Museum").cost().max()); // 100 per person × 4
        assertEquals(500.0, byName.get("Boat").cost().max()); // per group
        Map<String, Itinerary.BudgetLine> lines = new HashMap<>();
        it.budget().lines().forEach(l -> lines.put(l.category(), l));
        assertEquals(4000.0, lines.get("FOOD").max()); // 500 × 4 × 2 days
        assertEquals("USER_ENTERED", lines.get("FOOD").confidence());
        assertEquals(3000.0, lines.get("ACCOMMODATION").max()); // 1 night
        assertTrue(lines.get("FUEL").max() > 0);
        assertEquals("CURATED_ESTIMATE", lines.get("FUEL").confidence());
        assertTrue(it.budget().complete());
        assertNotNull(a);
        assertNotNull(b);
    }

    @Test
    void mustVisitTooLongForTheDayIsInfeasibleWithReason() {
        PlaceSummary p = add("Long Trek", "adventure", 0.01, 0, 600, 720, "HIGH", List.of(), "north");
        ApiException e = fails("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"adventure\":5},"
                + "\"dayStart\":\"09:00\",\"dayEnd\":\"17:00\",\"mustVisit\":[\"" + p.id() + "\"]");
        assertEquals("INFEASIBLE_PLAN", e.errorCode());
        assertTrue(e.details().get("conflicts").toString().contains("Long Trek"));
    }

    @Test
    void openingHoursAreRespectedAndConflictsReported() {
        PlaceSummary night = add("Night Market", "shopping", 0.01, 0, 60, 90);
        hoursFor(night, "Mo-Su 18:00-23:00");
        PlaceSummary fort = add("Fort", "heritage", 0.015, 0, 60, 90);
        hoursFor(fort, "Mo-Su 14:00-18:00");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"shopping\":5,\"heritage\":5},"
                + "\"dayEnd\":\"17:00\"");
        assertFalse(names(it).contains("Night Market"));
        assertTrue(it.unscheduled().stream()
                .anyMatch(u -> u.name().equals("Night Market") && u.reason().contains("opening hours")));
        assertTrue(it.coverage().uncovered().contains("shopping"));
        Itinerary.Stop f = allStops(it).stream()
                .filter(s -> s.place().name().equals("Fort"))
                .findFirst()
                .orElseThrow();
        assertFalse(f.start().isBefore(LocalTime.of(14, 0)));
        assertEquals("CHECKED", f.hours().status());
    }

    @Test
    void weeklyClosureMovesMustVisitToAnOpenDay() {
        PlaceSummary m = add("Museum", "culture", 0.01, 0, 60, 90);
        hoursFor(m, "Sa-Th 10:00-17:00; Fr off");
        String req = "\"interests\":{\"culture\":5},\"mustVisit\":[\"" + m.id() + "\"]";
        assertEquals(
                "INFEASIBLE_PLAN",
                fails("\"startDate\":\"" + FRIDAY + "\"," + req).errorCode());
        Itinerary it = plan("\"startDate\":\"" + FRIDAY + "\",\"days\":2," + req);
        assertTrue(it.days().get(0).stops().isEmpty());
        assertEquals("Museum", it.days().get(1).stops().getFirst().place().name());
    }

    @Test
    void fixedBookingKeepsItsTimeAndImpossibleBookingIsRejected() {
        cluster(5, "food", 0.01);
        PlaceSummary spa = add("Spa", "family", 0.02, 0, 60, 120);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"food\":5},"
                + "\"fixedStops\":[{\"placeId\":\"" + spa.id() + "\",\"day\":1,\"start\":\"14:00\",\"minutes\":90}]");
        Itinerary.Stop s = it.days().getFirst().stops().stream()
                .filter(x -> x.place().name().equals("Spa"))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalTime.of(14, 0), s.start());
        assertEquals(LocalTime.of(15, 30), s.end());
        assertEquals("FIXED", s.kind());
        assertTimelinesFit(it);

        ApiException e = fails("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"food\":5},"
                + "\"fixedStops\":[{\"placeId\":\"" + spa.id() + "\",\"day\":1,\"start\":\"08:00\",\"minutes\":60}]");
        assertEquals("INFEASIBLE_PLAN", e.errorCode());
    }

    @Test
    void relaxedDayHasMoreFreeTimeThanPackedAndOverridesApply() {
        cluster(10, "food", 0.005);
        cluster(10, "beach", 0.01);
        String req = "\"startDate\":\"" + MONDAY + "\",\"interests\":{\"food\":5,\"beach\":5},";
        Itinerary relaxed = plan(req + "\"pace\":\"RELAXED\"");
        Itinerary packed = plan(req + "\"pace\":\"PACKED\"");
        Itinerary.Workload r = relaxed.days().getFirst().workload(),
                p = packed.days().getFirst().workload();
        assertTrue(r.freeMinutes() > p.freeMinutes(), r + " vs " + p);
        assertTrue(r.stops() < p.stops());
        assertTrue(r.utilisation() <= Pace.RELAXED.maxUtilisation() + 1e-9);
        assertTrue(p.utilisation() <= Pace.PACKED.maxUtilisation() + 1e-9);
        assertEquals("RELAXED", r.label());
        assertTimelinesFit(relaxed);
        assertTimelinesFit(packed);

        Itinerary mixed = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"interests\":{\"food\":5,\"beach\":5},"
                + "\"pace\":\"RELAXED\",\"dayOverrides\":[{\"day\":2,\"pace\":\"PACKED\",\"end\":\"21:00\"}]");
        assertEquals(Pace.RELAXED, mixed.days().get(0).pace());
        assertEquals(Pace.PACKED, mixed.days().get(1).pace());
        assertEquals(LocalTime.of(21, 0), mixed.days().get(1).end());
    }

    @Test
    void lateDinnerBreakNeverRunsPastMidnight() {
        // Dinner is still pending when a late stop is tried; a break from 23:xx used to reach 24:00 and crash.
        cluster(6, "beach", 0.005);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"interests\":{\"beach\":5},"
                + "\"dayStart\":\"09:00\",\"dayEnd\":\"23:59\","
                + "\"dayOverrides\":[{\"day\":2,\"start\":\"22:50\",\"pace\":\"RELAXED\"}]");
        assertTimelinesFit(it);
    }

    @Test
    void lunchIsTakenInItsWindowEvenWhenTheNextStopIsALateOpeningRestaurant() {
        // Seen in a Goa nightlife plan: a food stop opening in the evening deferred the lunch break to 18:27.
        PlaceSummary morning = add("Morning Beach", "beach", 0.004, 0, 60, 60);
        PlaceSummary night = add("Night Beach", "beach", 0.006, 0, 60, 60);
        PlaceSummary diner = add("Evening Diner", "food", 0.005, 0.002, 60, 60);
        hoursFor(diner, "Mo-Su 18:00-23:00");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},\"fillFreeTime\":false,"
                + "\"dayStart\":\"09:00\",\"dayEnd\":\"23:30\",\"mustVisit\":[\"" + diner.id() + "\"],"
                + "\"fixedStops\":[{\"placeId\":\"" + morning.id()
                + "\",\"day\":1,\"start\":\"09:30\",\"minutes\":180},"
                + "{\"placeId\":\"" + night.id() + "\",\"day\":1,\"start\":\"21:30\",\"minutes\":60}]");
        assertTimelinesFit(it);
        Itinerary.Day d = it.days().getFirst();
        assertTrue(names(it).contains("Evening Diner"), names(it).toString());
        Itinerary.Meal lunch = d.meals().stream()
                .filter(m -> m.label().equals("Lunch"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no lunch: " + d.meals()));
        assertFalse(lunch.start().isAfter(LocalTime.of(15, 30)), "lunch at " + lunch.start());
        assertFalse(lunch.start().isBefore(LocalTime.of(12, 0)), "lunch at " + lunch.start());
    }

    @Test
    void clubWithUnparseableHoursGetsTheEveningEstimateNotAnAfternoonSlot() {
        PlaceSummary club = add("Sunset Club", "nightlife", 0.004, 0, 120, 150);
        hoursFor(club, "sunset till late");
        cluster(4, "beach", 0.006);
        Itinerary it = plan("\"startDate\":\"" + FRIDAY + "\",\"interests\":{\"nightlife\":5,\"beach\":3},"
                + "\"mustVisit\":[\"" + club.id() + "\"],\"dayStart\":\"11:30\",\"dayEnd\":\"03:00\"");
        Itinerary.Stop s = allStops(it).stream()
                .filter(x -> x.place().id().equals(club.id()))
                .findFirst()
                .orElseThrow();
        assertFalse(s.start().isBefore(LocalTime.of(20, 0)) && s.start().isAfter(LocalTime.of(3, 0)), s.start() + "");
        assertEquals("ESTIMATED", s.hours().status());
    }

    @Test
    void dayCanEndAfterMidnightAndBarsGetEstimatedEveningHours() {
        PlaceSummary pub = add("Night Pub", "nightlife", 0.004, 0, 120, 150);
        cluster(4, "beach", 0.006);
        Itinerary it = plan("\"startDate\":\"" + FRIDAY + "\",\"interests\":{\"nightlife\":5,\"beach\":3},"
                + "\"mustVisit\":[\"" + pub.id() + "\"],\"dayStart\":\"11:30\",\"dayEnd\":\"03:00\"");
        Itinerary.Day d = it.days().getFirst();
        assertEquals(LocalTime.of(3, 0), d.end());
        Itinerary.Stop s = d.stops().stream()
                .filter(x -> x.place().id().equals(pub.id()))
                .findFirst()
                .orElseThrow();
        // Category "bar" without sourced hours: open 20:00-03:00, labelled an estimate.
        assertFalse(s.start().isBefore(LocalTime.of(20, 0)) && s.start().isAfter(LocalTime.of(3, 0)), s.start() + "");
        assertEquals("ESTIMATED", s.hours().status());
        assertEquals("CURATED_ESTIMATE", s.hours().confidence());

        assertEquals(
                "BAD_DAY_WINDOW",
                fails("\"dayStart\":\"20:00\",\"dayEnd\":\"07:00\",\"interests\":{\"beach\":5}")
                        .errorCode());
    }

    @Test
    void mustVisitIncludedAndAvoidNeverIncluded() {
        cluster(6, "beach", 0.01);
        PlaceSummary far = add("Far Temple", "spiritual", 0.2, 0.1, 60, 60);
        UUID avoided = id("beach 0");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5}," + "\"mustVisit\":[\""
                + far.id() + "\"],\"avoid\":[\"" + avoided + "\"]");
        assertTrue(names(it).contains("Far Temple"));
        assertTrue(allStops(it).stream().noneMatch(s -> s.place().id().equals(avoided)));
        assertTrue(allStops(it).stream()
                .anyMatch(s -> s.place().name().equals("Far Temple") && s.kind().equals("MUST_VISIT")));
    }

    @Test
    void removingAStopWithoutRefillKeepsOnlyTheKeptStops() {
        cluster(6, "beach", 0.01);
        UUID a = id("beach 1"), b = id("beach 2"), removed = id("beach 0");
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},\"fillFreeTime\":false,"
                + "\"keepStops\":[{\"placeId\":\"" + a + "\",\"day\":1},{\"placeId\":\"" + b + "\",\"day\":1}],"
                + "\"avoid\":[\"" + removed + "\"]");
        assertEquals(Set.of("beach 1", "beach 2"), names(it));
        assertTimelinesFit(it);
    }

    @Test
    void daysStayInOneAreaInsteadOfHopping() {
        for (int i = 0; i < 4; i++) {
            add("North beach " + i, "beach", 0.25 + 0.003 * i, 0, 60, 60, "LOW", List.of(), "north");
            add("North food " + i, "food", 0.25 - 0.003 * i, 0.003, 60, 60, "LOW", List.of(), "north");
            add("South beach " + i, "beach", -0.25 - 0.003 * i, 0, 60, 60, "LOW", List.of(), "south");
            add("South food " + i, "food", -0.25 + 0.003 * i, 0.003, 60, 60, "LOW", List.of(), "south");
        }
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"days\":2,\"interests\":{\"beach\":5,\"food\":5}");
        for (Itinerary.Day d : it.days()) {
            assertFalse(d.stops().isEmpty());
            assertEquals(
                    1,
                    d.stops().stream()
                            .map(s -> s.place().regionSlug())
                            .distinct()
                            .count(),
                    "day " + d.day() + " hops regions");
        }
    }

    @Test
    void repairDropsKeptStopsToFitAMustVisit() {
        PlaceSummary k1 = add("Kept A", "beach", 0.005, 0, 60, 60);
        PlaceSummary k2 = add("Kept B", "beach", 0.006, 0, 60, 60);
        PlaceSummary k3 = add("Kept C", "beach", 0.007, 0, 60, 60);
        PlaceSummary must = add("Must Fort", "heritage", 0.008, 0, 90, 90);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5,\"heritage\":3},"
                + "\"pace\":\"PACKED\",\"dayStart\":\"09:00\",\"dayEnd\":\"13:00\","
                + "\"keepStops\":[{\"placeId\":\"" + k1.id() + "\",\"day\":1},{\"placeId\":\"" + k2.id()
                + "\",\"day\":1},{\"placeId\":\"" + k3.id() + "\",\"day\":1}],"
                + "\"mustVisit\":[\"" + must.id() + "\"]");
        assertTrue(names(it).contains("Must Fort"));
        assertFalse(it.repairs().isEmpty());
        assertTrue(it.unscheduled().stream().anyMatch(u -> u.reason().contains("make room")));
        assertTimelinesFit(it);
    }

    @Test
    void invalidRequestsAreRejected() {
        cluster(2, "beach", 0.01);
        assertEquals("BAD_DAYS", fails("\"days\":0,\"interests\":{\"beach\":5}").errorCode());
        assertEquals(
                "BAD_DAY_WINDOW",
                fails("\"dayStart\":\"18:00\",\"dayEnd\":\"09:00\",\"interests\":{\"beach\":5}")
                        .errorCode());
        assertEquals(
                "BAD_PARTY",
                fails("\"partySize\":0,\"interests\":{\"beach\":5}").errorCode());
        assertEquals(
                "BAD_BUDGET",
                fails("\"budget\":{\"amount\":-5,\"basis\":\"TOTAL\",\"strictness\":\"HARD\"},"
                                + "\"interests\":{\"beach\":5}")
                        .errorCode());
    }

    @Test
    void foodStopAtLunchtimeIsTheLunchAndFoodStopsAreCapped() {
        for (int i = 0; i < 10; i++) {
            PlaceSummary r = add("Restaurant " + i, "food", 0.004 * i, 0.002, 60, 90);
            assertNotNull(r);
        }
        PlaceSummary lunch = add("Lunch Spot", "food", 0.01, 0, 60, 60);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"food\":5},"
                + "\"fixedStops\":[{\"placeId\":\"" + lunch.id() + "\",\"day\":1,\"start\":\"13:00\"}]");
        Itinerary.Day d = it.days().getFirst();
        assertTrue(
                d.stops().size() <= ItineraryPlanner.MAX_FOOD_STOPS_PER_DAY,
                d.stops().toString());
        assertEquals(1, d.meals().size(), d.meals().toString());
        assertEquals("Lunch Spot", d.meals().getFirst().place());
        assertEquals(LocalTime.of(13, 0), d.meals().getFirst().start());
        assertEquals(0, d.workload().mealMinutes());
        assertTimelinesFit(it);
    }

    // ---------------------------------------------------------------- Phase 7: route ordering

    @Test
    void closedDailyTourVisitsEachStopOnceAndReturnsToBase() {
        cluster(6, "beach", 0.02);
        cluster(4, "heritage", -0.015);
        Itinerary it = plan(
                "\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5,\"heritage\":5}," + "\"pace\":\"PACKED\"");
        Itinerary.Day d = it.days().getFirst();
        int n = d.stops().size();
        assertTrue(n >= 3);
        Itinerary.Ordering o = d.ordering();
        assertEquals("double-tree", o.method());
        assertTrue(o.metric(), o.metricViolations().toString());
        assertTrue(o.guaranteeApplies());
        assertTrue(o.mstCost() <= o.exactCost() + 1e-9, "MST is a lower bound");
        assertTrue(o.doubleTreeCost() <= 2 * o.exactCost() + 1e-9, "2-approximation");
        assertTrue(o.exactCost() <= o.nearestNeighbourCost() + 1e-9);
        assertTrue(o.exactCost() <= o.insertionCost() + 1e-9);
        Route r = d.route();
        assertEquals(n + 1, r.legs().size(), "base → stops → base");
        assertArrayEquals(new double[] {BLON, BLAT}, r.geometry().getFirst(), 1e-9);
        assertArrayEquals(new double[] {BLON, BLAT}, r.geometry().getLast(), 1e-9);
        assertEquals(n + 2, r.geometry().size()); // straight-line estimate draws base, stops, base
        assertFalse(d.navigationUrls().isEmpty());
        assertTrue(d.navigationUrls().getFirst().contains("origin=15.500000,73.800000"));
        assertEquals("ESTIMATE", it.travelProvider());
        assertTimelinesFit(it);
    }

    @Test
    void nonMetricTravelTimesGetNoApproximationClaim() {
        cluster(6, "beach", 0.02);
        travel = new TravelProvider() {
            @Override
            public TravelMatrix matrix(List<LatLon> pts, TransportMode mode) {
                int n = pts.size();
                int[][] m = new int[n][n];
                double[][] km = new double[n][n];
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (i != j) m[i][j] = i == 0 || j == 0 ? 5 : 40; // stop↔stop longer than via base
                return new TravelMatrix(m, km, "TEST", "test", 0, List.of());
            }

            @Override
            public Route route(List<LatLon> w, TransportMode mode) {
                return EstimateTravel.INSTANCE.route(w, mode);
            }
        };
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},\"pace\":\"PACKED\"");
        Itinerary.Ordering o = it.days().getFirst().ordering();
        assertTrue(it.days().getFirst().stops().size() >= 2);
        assertFalse(o.metric());
        assertFalse(o.guaranteeApplies());
        assertFalse(o.metricViolations().isEmpty());
        assertTimelinesFit(it);
    }

    @Test
    void openRouteDayEndsAtLastStopWithoutAGuarantee() {
        cluster(5, "beach", 0.02);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},\"returnToBase\":false");
        Itinerary.Day d = it.days().getFirst();
        assertEquals("double-tree-open-path", d.ordering().method());
        assertFalse(d.ordering().guaranteeApplies());
        assertEquals(d.stops().size(), d.route().legs().size(), "no return leg");
        assertEquals(0, d.returnTravelMinutes());
    }

    @Test
    void fixedBookingKeepsItsTimeWhenTheDayIsReordered() {
        cluster(6, "beach", 0.02);
        PlaceSummary spa = add("Spa", "family", -0.02, 0.01, 60, 60);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5},\"pace\":\"PACKED\","
                + "\"fixedStops\":[{\"placeId\":\"" + spa.id() + "\",\"day\":1,\"start\":\"11:00\"}]");
        Itinerary.Stop s = it.days().getFirst().stops().stream()
                .filter(x -> x.place().name().equals("Spa"))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalTime.of(11, 0), s.start());
        assertTimelinesFit(it);
    }

    @Test
    void routingOutageStillProducesALabelledPlan() {
        cluster(4, "beach", 0.02);
        travel = new RoutingService("http://127.0.0.1:9", 300);
        Itinerary it = plan("\"startDate\":\"" + MONDAY + "\",\"interests\":{\"beach\":5}");
        assertFalse(it.days().getFirst().stops().isEmpty());
        assertEquals("ESTIMATE", it.travelProvider());
        assertTrue(
                it.warnings().stream().anyMatch(w -> w.contains("unavailable")),
                it.warnings().toString());
    }
}
