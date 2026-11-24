package com.tripweaver.search;

import java.util.UUID;

/**
 * Autocomplete entry. {@code type} is TOWN, PLACE, CATEGORY, INTEREST, CUISINE, NEIGHBOURHOOD or REGION;
 * {@code value} is the filter value (category name, region slug, town key, …); {@code placeId} is set for PLACE;
 * {@code lat}/{@code lon} are set for TOWN.
 */
public record Suggestion(String type, String label, String value, UUID placeId, String detail, Double lat, Double lon) {

    public Suggestion(String type, String label, String value, UUID placeId, String detail) {
        this(type, label, value, placeId, detail, null, null);
    }
}
