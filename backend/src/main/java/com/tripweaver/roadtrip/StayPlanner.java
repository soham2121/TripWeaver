package com.tripweaver.roadtrip;

import com.tripweaver.place.ingest.OsmCategoryMapper;
import com.tripweaver.planning.Itinerary;
import com.tripweaver.planning.ItineraryRequest;
import com.tripweaver.planning.Pace;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stay at a road trip's destination: which days it covers and the itinerary request that plans them. Arrival by
 * {@link #ARRIVAL_CUTOFF} (inclusive) makes the arrival date stay day 1, starting {@link #SETTLE_MINUTES} after
 * arrival but not before the itinerary's day start; a later arrival starts the stay the next morning. Pure.
 */
public final class StayPlanner {

    static final int MAX_DAYS = 14, SETTLE_MINUTES = 60;
    static final LocalTime ARRIVAL_CUTOFF = LocalTime.of(14, 0), DAY_START = LocalTime.of(9, 0);
    /** Weight of every interest when the road trip has none ticked. */
    static final double DEFAULT_WEIGHT = 3.0;

    private StayPlanner() {}

    /** The chosen interests, or every interest at equal weight when none are chosen. */
    static Map<String, Double> interestsOrAll(Map<String, Double> chosen) {
        if (!chosen.isEmpty()) return chosen;
        Map<String, Double> all = new LinkedHashMap<>();
        for (String i : OsmCategoryMapper.INTERESTS) all.put(i, DEFAULT_WEIGHT);
        return all;
    }

    static boolean arrivalDayCounts(LocalDateTime arrival) {
        return !arrival.toLocalTime().isAfter(ARRIVAL_CUTOFF);
    }

    public static LocalDate firstDay(LocalDateTime arrival) {
        return arrivalDayCounts(arrival) ? arrival.toLocalDate() : arrival.toLocalDate().plusDays(1);
    }

    /** Start of stay day 1 when it is the arrival day; null when the stay starts the next morning. */
    public static LocalTime firstDayStart(LocalDateTime arrival) {
        if (!arrivalDayCounts(arrival)) return null;
        LocalTime t = arrival.toLocalTime().plusMinutes(SETTLE_MINUTES);
        return t.isBefore(DAY_START) ? DAY_START : t;
    }

    /**
     * Itinerary request for the stay, based at {@code base} (the destination with resolved coordinates) with the road
     * trip's party, interests and costs.
     */
    public static ItineraryRequest request(
            RoadTripRequest req, RoadTripRequest.Waypoint base, LocalDateTime arrival, String destination, long seed) {
        Map<String, Double> interests = interestsOrAll(req.interests());
        LocalTime start = firstDayStart(arrival);
        var b = base;
        // ponytail: accommodation left out; the itinerary budget counts days - 1 nights, a stay sleeps more.
        var costs = new ItineraryRequest.Costs(
                req.foodPerPersonPerDay(), null, req.fuelPricePerLitre(), req.mileageKmPerLitre(), null);
        return new ItineraryRequest(
                destination,
                firstDay(arrival),
                req.stayDays(),
                req.partySize(),
                interests,
                b.lat(),
                b.lon(),
                b.name(),
                req.transport(),
                Pace.BALANCED,
                null,
                null,
                true,
                null,
                costs,
                null,
                start == null ? null : List.of(new ItineraryRequest.DayOverride(1, start, null, null, null)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                seed);
    }

    /** Nights slept at the destination: from the arrival night up to the morning after the last stay day. */
    static int nights(int stayDays, LocalDateTime arrival) {
        LocalDate last = firstDay(arrival).plusDays(stayDays - 1L);
        return (int) ChronoUnit.DAYS.between(arrival.toLocalDate(), last.plusDays(1));
    }

    /** The stay record: days, nights slept at the destination, and the planned itinerary or a note. */
    public static RoadTrip.Stay stay(
            RoadTripRequest req, LocalDateTime arrival, String destination, Itinerary itinerary, String note) {
        LocalDate first = firstDay(arrival);
        LocalDate last = first.plusDays(req.stayDays() - 1L);
        return new RoadTrip.Stay(req.stayDays(), first, last, nights(req.stayDays(), arrival), destination, itinerary, note);
    }
}
