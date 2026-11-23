package com.tripweaver.roadtrip;

import com.tripweaver.place.PlaceSummary;
import com.tripweaver.planning.Itinerary;
import com.tripweaver.routing.Route;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A planned road trip: driving days, fixed segments with optional detours, costs and labels. */
public record RoadTrip(
        List<Day> days,
        List<Segment> segments,
        Itinerary.Budget budget,
        LocalDateTime departure,
        LocalDateTime arrival,
        int totalDrivingMinutes,
        double totalKm,
        int nights,
        int detourCandidates,
        List<String> warnings,
        String travelProvider,
        String travelBasis,
        String disclaimer,
        long seed) {

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

    /** kind: USER (an overnight waypoint) or SUGGESTED (OSM-listed accommodation near the route). */
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
