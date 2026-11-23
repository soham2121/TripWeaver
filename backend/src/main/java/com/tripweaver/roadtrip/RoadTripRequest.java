package com.tripweaver.roadtrip;

import com.tripweaver.routing.TransportMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Road-trip inputs (plan section 7). Origin, waypoints and destination are fixed and never reordered. Detours are
 * optional and only requested when {@code interests} is non-empty.
 *
 * @param arriveBy hard: latest arrival at the destination
 * @param dayEnd hard: no driving or visits after this time on any day
 * @param maxDetourMinutes added driving per detour (beyond the direct route)
 * @param maxTotalDetourMinutes added driving plus visit time over all detours
 */
public record RoadTripRequest(
        Waypoint origin,
        Waypoint destination,
        List<Waypoint> waypoints,
        LocalDate departureDate,
        LocalTime departureTime,
        LocalDateTime arriveBy,
        Double maxDrivingHoursPerDay,
        Integer maxContinuousDrivingMinutes,
        Integer restBreakMinutes,
        Integer mealMinutes,
        LocalTime dayStart,
        LocalTime dayEnd,
        Map<String, Double> interests,
        Integer maxDetourMinutes,
        Integer maxDetours,
        Integer maxTotalDetourMinutes,
        Boolean suggestOvernight,
        Integer partySize,
        TransportMode transport,
        Double mileageKmPerLitre,
        Double fuelPricePerLitre,
        Double tollsTotal,
        Double accommodationPerNight,
        Double foodPerPersonPerDay,
        Set<UUID> avoid,
        Long seed) {

    /** A fixed stop: coordinates or a catalogue place. {@code overnight} ends the day there. */
    public record Waypoint(String name, Double lat, Double lon, UUID placeId, Boolean overnight, Integer stayMinutes) {}

    public RoadTripRequest {
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        departureTime = departureTime == null ? LocalTime.of(7, 0) : departureTime;
        maxDrivingHoursPerDay = maxDrivingHoursPerDay == null ? 7.0 : maxDrivingHoursPerDay;
        maxContinuousDrivingMinutes = maxContinuousDrivingMinutes == null ? 120 : maxContinuousDrivingMinutes;
        restBreakMinutes = restBreakMinutes == null ? 15 : restBreakMinutes;
        mealMinutes = mealMinutes == null ? 45 : mealMinutes;
        dayStart = dayStart == null ? LocalTime.of(8, 0) : dayStart;
        dayEnd = dayEnd == null ? LocalTime.of(20, 0) : dayEnd;
        interests = interests == null ? Map.of() : Map.copyOf(interests);
        maxDetourMinutes = maxDetourMinutes == null ? 45 : maxDetourMinutes;
        maxDetours = maxDetours == null ? 3 : maxDetours;
        maxTotalDetourMinutes = maxTotalDetourMinutes == null ? 180 : maxTotalDetourMinutes;
        suggestOvernight = suggestOvernight == null || suggestOvernight;
        partySize = partySize == null ? 1 : partySize;
        transport = transport == null ? TransportMode.CAR : transport;
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
