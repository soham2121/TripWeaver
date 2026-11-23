package com.tripweaver.planning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trip-level and day-level planning inputs (plan sections 3.1–3.4). Nullable fields use defaults.
 *
 * @param fixedStops hard: booked visits at a fixed day and time
 * @param keepStops stops kept from a previous plan on a given day (re-planning); repair may drop them to fit a
 *     must-visit, and says so
 * @param userCosts user-entered per-person cost for a place (INR), labelled USER_ENTERED
 */
public record ItineraryRequest(
        String destination,
        LocalDate startDate,
        Integer days,
        Integer partySize,
        Map<String, Double> interests,
        Double baseLat,
        Double baseLon,
        String baseName,
        TransportMode transport,
        Pace pace,
        LocalTime dayStart,
        LocalTime dayEnd,
        Boolean returnToBase,
        Budget budget,
        Costs costs,
        Map<UUID, Double> userCosts,
        List<DayOverride> dayOverrides,
        List<FixedStop> fixedStops,
        List<KeepStop> keepStops,
        Set<UUID> mustVisit,
        Set<UUID> avoid,
        Boolean requireWheelchair,
        Set<String> regions,
        Double maxEntryCostPerPerson,
        Long seed) {

    public enum BudgetBasis {
        PER_PERSON,
        TOTAL
    }

    public enum Strictness {
        HARD,
        FLEXIBLE
    }

    public record Budget(Double amount, BudgetBasis basis, Strictness strictness) {}

    /** User-entered amounts (INR). Missing values stay unknown. */
    public record Costs(
            Double foodPerPersonPerDay,
            Double accommodationPerNight,
            Double fuelPricePerLitre,
            Double mileageKmPerLitre,
            Double miscBufferPercent) {}

    /** Per-day override; {@code day} is 1-based. */
    public record DayOverride(int day, LocalTime start, LocalTime end, Pace pace, Boolean returnToBase) {}

    /** {@code minutes} null = the pace's visit duration. */
    public record FixedStop(UUID placeId, int day, LocalTime start, Integer minutes) {}

    public record KeepStop(UUID placeId, int day) {}

    public ItineraryRequest {
        interests = interests == null ? Map.of() : Map.copyOf(interests);
        userCosts = userCosts == null ? Map.of() : Map.copyOf(userCosts);
        dayOverrides = dayOverrides == null ? List.of() : List.copyOf(dayOverrides);
        fixedStops = fixedStops == null ? List.of() : List.copyOf(fixedStops);
        keepStops = keepStops == null ? List.of() : List.copyOf(keepStops);
        mustVisit = mustVisit == null ? Set.of() : Set.copyOf(mustVisit);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
        regions = regions == null ? Set.of() : Set.copyOf(regions);
        days = days == null ? 1 : days;
        partySize = partySize == null ? 1 : partySize;
        transport = transport == null ? TransportMode.CAR : transport;
        pace = pace == null ? Pace.BALANCED : pace;
        dayStart = dayStart == null ? LocalTime.of(9, 0) : dayStart;
        dayEnd = dayEnd == null ? LocalTime.of(19, 0) : dayEnd;
        returnToBase = returnToBase == null || returnToBase;
        requireWheelchair = requireWheelchair != null && requireWheelchair;
        costs = costs == null ? new Costs(null, null, null, null, null) : costs;
    }
}
