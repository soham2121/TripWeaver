package com.tripweaver.place;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Compact place view used by search, discovery and the planner. */
public record PlaceSummary(
        UUID id,
        String name,
        double lat,
        double lon,
        String primaryCategory,
        Set<String> categories,
        Set<String> interests,
        String regionSlug,
        String regionName,
        String neighbourhood,
        boolean activity,
        Integer visitMinutesMin,
        Integer visitMinutesMax,
        String intensity,
        List<CostEstimate> costs,
        boolean hasOpeningHours,
        String dataQuality,
        List<String> missingFields) {

    /** A sourced cost. Absence of a cost means unknown, never zero. */
    public record CostEstimate(String kind, double min, double max, String currency, String basis, String confidence) {}
}
