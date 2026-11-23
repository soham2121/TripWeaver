package com.tripweaver.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** Full place view with field-level provenance. */
public record PlaceDetail(
        PlaceSummary summary,
        String description,
        String website,
        String osmRef,
        String osmUrl,
        Seasonal seasonal,
        Set<String> accessibility,
        List<Cost> costs,
        List<Hours> openingHours,
        List<Source> sources,
        String visitMinutesBasis,
        String visitMinutesConfidence,
        String attribution) {

    public record Seasonal(String notes, List<Integer> closedMonths) {}

    public record Cost(
            String kind,
            double min,
            double max,
            String currency,
            String basis,
            String confidence,
            String sourceType,
            String sourceUrl,
            OffsetDateTime verifiedAt,
            String note) {}

    public record Hours(
            String raw,
            String confidence,
            String sourceType,
            String sourceUrl,
            OffsetDateTime verifiedAt,
            String note) {}

    public record Source(
            String field,
            String sourceType,
            String sourceRef,
            String sourceUrl,
            String confidence,
            OffsetDateTime verifiedAt,
            String note) {}
}
