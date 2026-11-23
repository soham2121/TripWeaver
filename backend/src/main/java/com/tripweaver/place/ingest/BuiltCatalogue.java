package com.tripweaver.place.ingest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BuiltCatalogue(Destination destination, List<Region> regions, List<BuiltPlace> places, Report report) {

    public record Destination(
            UUID id, String slug, String name, double minLat, double minLon, double maxLat, double maxLon) {}

    public record Region(UUID id, String slug, String name, double lat, double lon) {}

    /** Accepted / rejected / merged / unresolved summary (plan section 4.3, step 10). */
    public record Report(
            String sourceFile,
            String sourceTimestamp,
            int candidates,
            int accepted,
            int merged,
            Map<String, Integer> rejected,
            List<String> mergedExamples,
            List<String> unresolvedCurated,
            Map<String, Integer> byPrimaryCategory,
            Map<String, Integer> byRegion,
            Map<String, Integer> byInterest,
            Map<String, Integer> missingFieldCounts) {}
}
