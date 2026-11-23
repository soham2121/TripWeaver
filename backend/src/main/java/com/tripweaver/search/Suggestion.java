package com.tripweaver.search;

import java.util.UUID;

/**
 * Autocomplete entry. {@code type} is PLACE, CATEGORY, INTEREST, CUISINE, NEIGHBOURHOOD or REGION;
 * {@code value} is the filter value (category name, region slug, …); {@code placeId} is set for PLACE.
 */
public record Suggestion(String type, String label, String value, UUID placeId, String detail) {}
