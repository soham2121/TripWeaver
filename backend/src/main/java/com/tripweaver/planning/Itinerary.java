package com.tripweaver.planning;

import com.tripweaver.place.PlaceSummary;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A day-wise itinerary with timelines, workload, budget, coverage and everything that could not be planned. */
public record Itinerary(
        List<Day> days,
        Budget budget,
        Coverage coverage,
        List<Unscheduled> unscheduled,
        List<Repair> repairs,
        List<String> warnings,
        String travelBasis,
        String disclaimer,
        long seed,
        Stats stats) {

    public record Day(
            int day,
            LocalDate date,
            DayOfWeek dayOfWeek,
            LocalTime start,
            LocalTime end,
            Pace pace,
            boolean returnToBase,
            List<Stop> stops,
            List<Meal> meals,
            int returnTravelMinutes,
            LocalTime endOfDay,
            Workload workload,
            Money cost,
            List<String> warnings) {}

    /**
     * One visit. {@code kind}: FIXED (booked time), KEEP (kept from a previous plan), MUST_VISIT, COVER (Set
     * Cover pick) or FILL (ranked pool).
     */
    public record Stop(
            int order,
            PlaceSummary place,
            LocalTime arrive,
            LocalTime start,
            LocalTime end,
            int visitMinutes,
            String visitBasis,
            int travelMinutes,
            double travelKm,
            int waitMinutes,
            String kind,
            List<String> covers,
            Money cost,
            HoursInfo hours,
            String reason) {}

    /** Converted for the party. {@code min}/{@code max} are null when unknown — never zero. */
    public record Money(Double min, Double max, boolean unknown, String confidence, String note) {}

    /** status: CHECKED (parsed and enforced), UNPARSED (sourced but not machine-readable), UNKNOWN. */
    public record HoursInfo(String raw, String confidence, String status) {}

    /** A separate break ({@code place} null) or a food stop that serves as the meal. */
    public record Meal(String label, LocalTime start, LocalTime end, String place) {}

    /** Persisted workload metrics behind the pace label (plan section 3.3). */
    public record Workload(
            int windowMinutes,
            int activityMinutes,
            int travelMinutes,
            int mealMinutes,
            int bufferMinutes,
            int waitMinutes,
            int freeMinutes,
            double utilisation,
            int stops,
            int highIntensityStops,
            int regionTransitions,
            int intensityLoad,
            String label) {}

    public record Budget(
            String currency,
            int partySize,
            List<BudgetLine> lines,
            double knownMin,
            double knownMax,
            List<String> unknownItems,
            boolean complete,
            Double limit,
            String limitBasis,
            String strictness,
            String status,
            double knownMaxPerPerson) {}

    /** min/max null = unknown. */
    public record BudgetLine(
            String category, String item, Double min, Double max, String confidence, String basis, String note) {}

    public record Coverage(
            List<String> requested, List<String> covered, List<String> uncovered, Map<String, String> reasons) {}

    public record Unscheduled(UUID placeId, String name, String kind, String reason) {}

    public record Repair(String action, UUID placeId, String name, int day, String reason) {}

    public record Stats(int candidates, long insertionEvaluations, int repairs, int recommendationEligible) {}
}
