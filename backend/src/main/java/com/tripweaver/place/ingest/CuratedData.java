package com.tripweaver.place.ingest;

import java.util.List;

/** Curated, sourced field values (data/curated/&lt;destination&gt;-curated.json). */
public record CuratedData(String note, String verifiedAt, List<Rec> records) {

    /** Matched by {@code osmRef} ("node/123") when present, otherwise by exact normalised name. */
    public record Rec(String osmRef, String name, Hours openingHours, List<Cost> costs, Seasonal seasonal) {}

    public record Hours(String raw, String confidence, String sourceUrl, String note) {}

    public record Cost(
            String kind,
            double min,
            double max,
            String currency,
            String basis,
            String confidence,
            String sourceUrl,
            String note) {}

    public record Seasonal(String text, List<Integer> closedMonths, String confidence, String sourceUrl, String note) {}
}
