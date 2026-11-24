package com.tripweaver.roadtrip;

import com.tripweaver.place.PlaceSummary;
import com.tripweaver.planning.Itinerary;
import com.tripweaver.routing.Route;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A planned road trip: driving days, fixed segments with optional detours, costs and labels, and the stay at the
 * destination ({@code stay}, null without {@code stayDays}) and the ways home ({@code returnTrip}, null without
 * {@code planReturn}).
 */
public record RoadTrip(
        List<Day> days,
        List<Segment> segments,
        Itinerary.Budget budget,
        LocalDateTime departure,
        LocalDateTime arrival,
        int totalDrivingMinutes,
        double totalKm,
        int nights,
        List<NightStays> stayOptions,
        int detourCandidates,
        List<String> warnings,
        String travelProvider,
        String travelBasis,
        String disclaimer,
        long seed,
        Stay stay,
        ReturnTrip returnTrip) {

    /** This trip with its stay at the destination. */
    public RoadTrip withStay(Stay s) {
        return new RoadTrip(
                days, segments, budget, departure, arrival, totalDrivingMinutes, totalKm, nights, stayOptions,
                detourCandidates, warnings, travelProvider, travelBasis, disclaimer, seed, s, returnTrip);
    }

    /** This trip with its ways home. */
    public RoadTrip withReturn(ReturnTrip r) {
        return new RoadTrip(
                days, segments, budget, departure, arrival, totalDrivingMinutes, totalKm, nights, stayOptions,
                detourCandidates, warnings, travelProvider, travelBasis, disclaimer, seed, stay, r);
    }

    /** This trip with more warnings. */
    public RoadTrip withWarnings(List<String> more) {
        List<String> w = new java.util.ArrayList<>(warnings);
        w.addAll(more);
        return new RoadTrip(
                days, segments, budget, departure, arrival, totalDrivingMinutes, totalKm, nights, stayOptions,
                detourCandidates, List.copyOf(w), travelProvider, travelBasis, disclaimer, seed, stay, returnTrip);
    }

    /** The ways home; {@code chosen} is the option shown (1..4). */
    public record ReturnTrip(int chosen, List<ReturnOption> options) {}

    /**
     * One way home. kind: DIRECT, SAME_WAY, NEW_STOPS or DIFFERENT_TOWN. {@code via} is the town of a DIFFERENT_TOWN
     * option. {@code plan} is null when the option could not be built; {@code note} then says why (or remarks on a
     * built one).
     */
    public record ReturnOption(int number, String kind, String label, String via, RoadTrip plan, String note) {}

    /**
     * Days at the destination, from {@code firstDay} to {@code lastDay}. {@code nights} are slept there: from the
     * arrival night up to the morning after {@code lastDay}. {@code destination} (catalogue slug) and {@code
     * itinerary} are null when the destination has no place data or the stay could not be planned; {@code note}
     * then says why.
     */
    public record Stay(
            int days,
            LocalDate firstDay,
            LocalDate lastDay,
            int nights,
            String destination,
            Itinerary itinerary,
            String note) {}

    public record Day(
            int day,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int drivingMinutes,
            double km,
            int longestContinuousDrivingMinutes,
            List<Item> items,
            Overnight overnight,
            List<String> navigationUrls) {}

    /** type: DEPART, DRIVE, REST, MEAL, DETOUR, WAYPOINT, OVERNIGHT or ARRIVE. */
    public record Item(
            String type,
            String name,
            UUID placeId,
            Double lat,
            Double lon,
            LocalTime start,
            LocalTime end,
            int minutes,
            double km,
            String note) {}

    /**
     * Ranked towns for one night when the drive is split into {@code drivingDays}: towns closest to the even split
     * (driving time from the start), with the least extra driving off the route, ranked first.
     */
    public record NightStays(int night, int targetDriveMinutes, List<TownOption> options) {}

    /**
     * A gazetteer city or town near the route with places to stay. {@code key} goes back in {@code stayChoices};
     * {@code sights} counts things to do within 15 km and {@code stays} places to stay within 5 km (OSM listings).
     * {@code detourRating} (1–5, 5 = on the route) rates the extra driving to reach the town; {@code interestRating}
     * (0–5) rates {@code interestMatches}, the things to do within 15 km that match the trip's interests (all
     * interests when none are chosen).
     */
    public record TownOption(
            String town,
            String key,
            double lat,
            double lon,
            int driveFromStartMinutes,
            int detourMinutes,
            int sights,
            List<String> topSights,
            int stays,
            List<String> stayNames,
            int detourRating,
            int interestMatches,
            int interestRating,
            boolean chosen) {}

    /**
     * kind: USER (an overnight waypoint), TOWN (a town stay splitting the drive into the requested days) or SUGGESTED
     * (OSM-listed accommodation near the route, when the daily driving limit forces a stop).
     */
    public record Overnight(
            String name, UUID placeId, double lat, double lon, String kind, String confidence, String note) {}

    public record Segment(
            int index,
            String from,
            String to,
            int directMinutes,
            double directKm,
            List<DetourStop> detours,
            Ordering ordering,
            Route route) {}

    public record DetourStop(
            PlaceSummary place,
            int addedDriveMinutes,
            int visitMinutes,
            List<String> covers,
            double score,
            String reason) {}

    /**
     * Order of the detours between two fixed waypoints: the shorter of route order (by position along the route) and
     * a Double-Tree open path on symmetrised times. An open path has no approximation guarantee.
     */
    public record Ordering(
            String method,
            String applied,
            double routeOrderMinutes,
            Double doubleTreeMinutes,
            boolean guaranteeApplies) {}
}
