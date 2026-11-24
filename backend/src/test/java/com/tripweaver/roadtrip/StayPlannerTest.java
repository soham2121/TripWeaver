package com.tripweaver.roadtrip;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.place.ingest.OsmCategoryMapper;
import com.tripweaver.planning.ItineraryRequest;
import com.tripweaver.planning.Pace;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The stay at the destination: arrival rule, inputs carried over, days and nights. */
class StayPlannerTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static RoadTripRequest req(String extra) {
        return JSON.readValue(
                "{\"origin\":{\"name\":\"Mumbai\",\"lat\":18.94,\"lon\":72.835},"
                        + "\"destination\":{\"name\":\"Candolim\",\"lat\":15.52,\"lon\":73.766},"
                        + "\"departureDate\":\"2026-12-14\"" + extra + "}",
                RoadTripRequest.class);
    }

    static LocalDateTime at(String t) {
        return LocalDateTime.parse(t);
    }

    @Test
    void arrivalAtTwoCountsTheArrivalDay() {
        assertEquals(LocalDate.of(2026, 12, 15), StayPlanner.firstDay(at("2026-12-15T14:00")));
        assertEquals(LocalTime.of(15, 0), StayPlanner.firstDayStart(at("2026-12-15T14:00")));
    }

    @Test
    void earlyArrivalStartsAnHourLaterButNotBeforeNine() {
        assertEquals(LocalTime.of(9, 0), StayPlanner.firstDayStart(at("2026-12-15T07:30")));
        assertEquals(LocalTime.of(14, 30), StayPlanner.firstDayStart(at("2026-12-15T13:30")));
    }

    @Test
    void lateArrivalStartsNextMorning() {
        assertEquals(LocalDate.of(2026, 12, 16), StayPlanner.firstDay(at("2026-12-15T18:30")));
        assertNull(StayPlanner.firstDayStart(at("2026-12-15T18:30")));
    }

    @Test
    void requestCarriesInputsAndDefaultsInterests() {
        RoadTripRequest r = req(",\"stayDays\":2,\"partySize\":3,\"foodPerPersonPerDay\":900,"
                + "\"accommodationPerNight\":4000");
        ItineraryRequest i = StayPlanner.request(r, r.destination(), at("2026-12-15T09:06"), "goa", 7);
        assertEquals("goa", i.destination());
        assertEquals(LocalDate.of(2026, 12, 15), i.startDate());
        assertEquals(2, i.days());
        assertEquals(3, i.partySize());
        assertEquals("Candolim", i.baseName());
        assertEquals(15.52, i.baseLat());
        assertEquals(Set.copyOf(OsmCategoryMapper.INTERESTS), i.interests().keySet());
        assertEquals(900.0, i.costs().foodPerPersonPerDay());
        assertNull(i.costs().accommodationPerNight()); // nights are reported on the stay instead
        assertEquals(Pace.BALANCED, i.pace());
        assertEquals(7L, i.seed());
        assertEquals(LocalTime.of(10, 6), i.dayOverrides().getFirst().start());
        assertEquals(1, i.dayOverrides().getFirst().day());

        RoadTripRequest beach = req(",\"stayDays\":2,\"interests\":{\"beach\":5}");
        ItineraryRequest late = StayPlanner.request(beach, beach.destination(), at("2026-12-15T18:30"), "goa", 7);
        assertEquals(Set.of("beach"), late.interests().keySet());
        assertTrue(late.dayOverrides() == null || late.dayOverrides().isEmpty());
    }

    @Test
    void stayRecordsDaysAndNights() {
        RoadTrip.Stay s = StayPlanner.stay(req(",\"stayDays\":2"), at("2026-12-15T09:06"), "goa", null, null);
        assertEquals(2, s.days());
        assertEquals(LocalDate.of(2026, 12, 15), s.firstDay());
        assertEquals(LocalDate.of(2026, 12, 16), s.lastDay());
        assertEquals(2, s.nights());
        RoadTrip.Stay late = StayPlanner.stay(req(",\"stayDays\":2"), at("2026-12-15T18:30"), "goa", null, null);
        assertEquals(LocalDate.of(2026, 12, 16), late.firstDay());
        assertEquals(LocalDate.of(2026, 12, 17), late.lastDay());
        assertEquals(3, late.nights());
    }

    @Test
    void stayDaysOutOfRangeIsABadRequest() {
        var e = assertThrows(
                com.tripweaver.common.ApiException.class, () -> RoadTripPlanner.validate(req(",\"stayDays\":15")));
        assertEquals("BAD_STAY_DAYS", e.errorCode());
        assertDoesNotThrow(() -> RoadTripPlanner.validate(req(",\"stayDays\":0")));
    }
}
