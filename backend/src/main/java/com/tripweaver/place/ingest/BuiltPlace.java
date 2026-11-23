package com.tripweaver.place.ingest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A catalogue record ready to persist, with field-level provenance. */
public record BuiltPlace(
        UUID id,
        String osmType,
        long osmId,
        String name,
        String description,
        double lat,
        double lon,
        String regionSlug,
        String neighbourhood,
        CategoryMapping mapping,
        Set<String> accessibility,
        String website,
        Map<String, String> osmTags,
        List<Cost> costs,
        List<Hours> hours,
        Seasonal seasonal,
        List<Source> sources,
        String quality,
        List<String> missing) {

    public record Cost(
            String kind,
            double min,
            double max,
            String currency,
            String basis,
            String confidence,
            String sourceType,
            String sourceUrl,
            Instant verifiedAt,
            String note) {}

    public record Hours(
            String raw, String confidence, String sourceType, String sourceUrl, Instant verifiedAt, String note) {}

    public record Seasonal(String text, List<Integer> closedMonths, String confidence, String sourceUrl) {}

    public record Source(
            String field,
            String sourceType,
            String sourceRef,
            String sourceUrl,
            String confidence,
            Instant verifiedAt,
            String note) {}
}
