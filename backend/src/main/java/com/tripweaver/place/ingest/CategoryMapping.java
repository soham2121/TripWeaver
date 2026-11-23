package com.tripweaver.place.ingest;

import java.util.Set;

/**
 * TripWeaver classification of one OSM feature.
 *
 * @param activity false for accommodation (usable as a base, not as an itinerary activity)
 * @param intensity LOW, MODERATE or HIGH physical effort
 * @param visitMinutesMin category-default visit duration (curated estimate, not a measured value)
 */
public record CategoryMapping(
        String primaryCategory,
        Set<String> categories,
        Set<String> interests,
        boolean activity,
        String intensity,
        int visitMinutesMin,
        int visitMinutesMax) {}
