package com.tripweaver.roadtrip;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.common.ApiException;
import com.tripweaver.place.Town;
import com.tripweaver.place.ingest.OsmCategoryMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The four ways home, on the synthetic corridor of {@link RoadTripPlannerTest} (Origin 19.0N → Destination 15.5N). */
class ReturnPlannerTest {

    final RoadTripPlannerTest fx = new RoadTripPlannerTest();
    final List<RoadTripRequest> legs = new ArrayList<>();

    Function<RoadTripRequest, RoadTrip> leg() {
        return r -> {
            legs.add(r);
            return fx.plan(r);
        };
    }

    static final String SPLIT = "\"maxDrivingHoursPerDay\":5,\"maxContinuousDrivingMinutes\":120";
    static final String ONE_DAY = "\"maxDrivingHoursPerDay\":12,\"dayEnd\":\"23:00\"";

    static Town town(String name, double dLon) {
        return new Town(name, "town", RoadTripPlannerTest.lat(0.5), RoadTripPlannerTest.lon(0.5) + dLon, List.of());
    }

    @Test
    void returnDepartsTheDayAfterTheStay() {
        RoadTripRequest req = fx.request(ONE_DAY + ",\"stayDays\":2");
        RoadTrip out = fx.plan(req);
        RoadTrip.Stay stay = StayPlanner.stay(req, out.arrival(), null, null, "n");
        assertEquals(stay.lastDay().plusDays(1), ReturnPlanner.departure(out.withStay(stay)));
        assertEquals(out.arrival().toLocalDate().plusDays(1), ReturnPlanner.departure(out));
    }

    @Test
    void fourOptionsInOrderFromTheDestinationBackToTheOrigin() {
        fx.hotels();
        RoadTripRequest req = fx.request(SPLIT);
        RoadTrip.ReturnTrip rt = ReturnPlanner.plan(req, fx.plan(req), fx.towns, leg());
        assertEquals(
                List.of("DIRECT", "SAME_WAY", "NEW_STOPS", "DIFFERENT_TOWN"),
                rt.options().stream().map(RoadTrip.ReturnOption::kind).toList());
        assertEquals(
                List.of(1, 2, 3, 4),
                rt.options().stream().map(RoadTrip.ReturnOption::number).toList());
        assertEquals(
                List.of("Direct", "Same way back", "Same road, new stops"),
                rt.options().subList(0, 3).stream()
                        .map(RoadTrip.ReturnOption::label)
                        .toList());
        assertEquals("Destination", legs.getFirst().origin().name());
        assertEquals("Origin", legs.getFirst().destination().name());
        assertTrue(legs.getFirst().waypoints().isEmpty());
        assertTrue(legs.getFirst().interests().isEmpty());
        RoadTrip direct = rt.options().getFirst().plan();
        assertNull(direct.stay());
        assertNull(direct.returnTrip());
        assertEquals(1, rt.chosen());
    }

    @Test
    void sameWayReversesTheOutboundStopsAsFixedWaypoints() {
        fx.hotels();
        fx.attractions();
        RoadTripRequest req = fx.request(
                SPLIT + ",\"interests\":{\"heritage\":5},\"waypoints\":[" + RoadTripPlannerTest.wp("W", 0.15) + "]");
        RoadTrip out = fx.plan(req);
        List<String> outbound = RoadTripPlannerTest.items(out).stream()
                .filter(i -> Set.of("WAYPOINT", "DETOUR", "OVERNIGHT").contains(i.type()))
                .map(RoadTrip.Item::name)
                .toList();
        assertTrue(outbound.size() >= 3, outbound.toString());
        ReturnPlanner.plan(req, out, fx.towns, leg());
        RoadTripRequest same = legs.get(1);
        assertEquals(
                outbound.reversed(),
                same.waypoints().stream().map(RoadTripRequest.Waypoint::name).toList());
        assertTrue(same.interests().isEmpty(), "no new detours on the way back");
        assertTrue(same.waypoints().stream().anyMatch(w -> Boolean.TRUE.equals(w.overnight())));
    }

    @Test
    void newStopsAvoidsOutboundPlacesAndUsesEveryInterestWhenNoneAreChosen() {
        fx.hotels();
        fx.attractions();
        RoadTripRequest req = fx.request(SPLIT + ",\"interests\":{\"heritage\":5}");
        RoadTrip out = fx.plan(req);
        Set<UUID> used = new HashSet<>();
        for (RoadTrip.Item i : RoadTripPlannerTest.items(out))
            if (Set.of("DETOUR", "OVERNIGHT").contains(i.type()) && i.placeId() != null) used.add(i.placeId());
        assertFalse(used.isEmpty());
        ReturnPlanner.plan(req, out, fx.towns, leg());
        assertTrue(legs.get(2).avoid().containsAll(used));

        legs.clear();
        RoadTripRequest none = fx.request(SPLIT);
        ReturnPlanner.plan(none, fx.plan(none), fx.towns, leg());
        assertEquals(
                Set.copyOf(OsmCategoryMapper.INTERESTS), legs.get(2).interests().keySet());
    }

    @Test
    void noNewStopsIsSaid() {
        RoadTripRequest req = fx.request(ONE_DAY);
        RoadTrip.ReturnOption o =
                ReturnPlanner.plan(req, fx.plan(req), fx.towns, leg()).options().get(2);
        assertNotNull(o.plan());
        assertEquals("No new stops found on this road", o.note());
    }

    @Test
    void differentTownIsFarFromTheRouteAndWithinTheCap() {
        fx.towns.add(town("Nearby", 0.1)); // ≈ 10 km off the outbound road
        fx.towns.add(town("Remote", 3.0)); // ≈ 320 km off: far more than a third extra
        RoadTripRequest req = fx.request(ONE_DAY);
        RoadTrip out = fx.plan(req);
        RoadTrip.ReturnOption none =
                ReturnPlanner.plan(req, out, fx.towns, leg()).options().get(3);
        assertNull(none.plan());
        assertEquals("No town off the outbound road within a third more driving", none.note());

        fx.towns.add(town("Inland", 0.8)); // ≈ 85 km off, ≈ 10% extra
        RoadTrip.ReturnOption via =
                ReturnPlanner.plan(req, out, fx.towns, leg()).options().get(3);
        assertEquals("Inland", via.via());
        assertEquals("Through Inland", via.label());
        assertNotNull(via.plan());
        assertTrue(RoadTripPlannerTest.items(via.plan()).stream()
                .anyMatch(i -> "WAYPOINT".equals(i.type()) && "Inland".equals(i.name())));
    }

    @Test
    void aFailingOptionDoesNotStopTheOthers() {
        RoadTripRequest req = fx.request(ONE_DAY);
        RoadTrip out = fx.plan(req);
        int[] n = {0};
        RoadTrip.ReturnTrip rt = ReturnPlanner.plan(req, out, fx.towns, r -> {
            if (++n[0] == 2) throw new IllegalStateException("boom");
            return fx.plan(r);
        });
        assertNull(rt.options().get(1).plan());
        assertEquals("boom", rt.options().get(1).note());
        assertNotNull(rt.options().get(0).plan());
        assertNotNull(rt.options().get(2).plan());
    }

    @Test
    void chosenFallsBackToDirectWhenThePickHasNoPlan() {
        RoadTripRequest req = fx.request(ONE_DAY + ",\"returnChoice\":4");
        RoadTrip.ReturnTrip rt = ReturnPlanner.plan(req, fx.plan(req), fx.towns, leg());
        assertNull(rt.options().get(3).plan());
        assertEquals(1, rt.chosen());
        RoadTripRequest two = fx.request(ONE_DAY + ",\"returnChoice\":2");
        assertEquals(2, ReturnPlanner.plan(two, fx.plan(two), fx.towns, leg()).chosen());
    }

    @Test
    void badReturnChoice() {
        var e = assertThrows(ApiException.class, () -> RoadTripPlanner.validate(fx.request("\"returnChoice\":5")));
        assertEquals("BAD_RETURN_CHOICE", e.errorCode());
    }

    @Test
    void everyLegLeavesTheDayAfterArrivalWithoutAStay() {
        RoadTripRequest req = fx.request(ONE_DAY);
        RoadTrip out = fx.plan(req);
        ReturnPlanner.plan(req, out, fx.towns, leg());
        LocalDate d = out.arrival().toLocalDate().plusDays(1);
        assertTrue(legs.stream().allMatch(l -> d.equals(l.departureDate()) && l.stayDays() == 0 && !l.planReturn()));
    }

    @Test
    void sameWayKeepsTheOutboundNightsWhenTheDriveIsSplitIntoDays() {
        fx.town("Early", 0.2, 0.01, 2, 0);
        fx.town("Midtown", 0.5, 0.01, 2, 1);
        fx.town("Busy", 0.58, 0.01, 1, 20);
        RoadTripRequest req = fx.request("\"drivingDays\":2," + ONE_DAY);
        RoadTrip out = fx.plan(req);
        assertEquals(2, out.days().size());
        RoadTrip same =
                ReturnPlanner.plan(req, out, fx.towns, leg()).options().get(1).plan();
        assertEquals(2, same.days().size(), "no extra town night on top of the reversed one");
        assertEquals(
                out.days().getFirst().overnight().name(),
                same.days().getFirst().overnight().name());
    }

    @Test
    void aUserOvernightStopIsReversedOnceNotTwice() {
        RoadTripRequest req = fx.request(ONE_DAY + ",\"waypoints\":[{\"name\":\"Stopover\",\"lat\":"
                + RoadTripPlannerTest.lat(0.5) + ",\"lon\":" + RoadTripPlannerTest.lon(0.5)
                + ",\"overnight\":true,\"stayMinutes\":30}]");
        RoadTrip out = fx.plan(req);
        ReturnPlanner.plan(req, out, fx.towns, leg());
        List<RoadTripRequest.Waypoint> back = legs.get(1).waypoints();
        assertEquals(1, back.size(), back.toString());
        assertEquals("Stopover", back.getFirst().name());
        assertTrue(back.getFirst().overnight());
        assertEquals(30, back.getFirst().stayMinutes());
    }

    @Test
    void aFailureWithoutAMessageStillSaysWhy() {
        RoadTripRequest req = fx.request(ONE_DAY);
        RoadTrip out = fx.plan(req);
        int[] n = {0};
        RoadTrip.ReturnTrip rt = ReturnPlanner.plan(req, out, fx.towns, r -> {
            if (++n[0] == 1) throw new IllegalStateException();
            return fx.plan(r);
        });
        assertEquals("Could not be planned", rt.options().getFirst().note());
    }

    @Test
    void differentTownAttemptsAreCapped() {
        fx.hotels();
        for (int k = 0; k < 8; k++) fx.towns.add(town("Inland " + k, 0.7 + k * 0.02));
        RoadTripRequest req = fx.request(SPLIT);
        RoadTrip out = fx.plan(req);
        int[] via = {0};
        ReturnPlanner.plan(req, out, fx.towns, r -> {
            if (r.waypoints().stream().anyMatch(w -> w.name().startsWith("Inland"))) {
                via[0]++;
                throw ApiException.badRequest("INFEASIBLE_PLAN", "no");
            }
            return fx.plan(r);
        });
        assertTrue(via[0] <= 3, via[0] + " different-town attempts");
    }
}
