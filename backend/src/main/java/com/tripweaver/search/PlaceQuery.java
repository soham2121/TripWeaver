package com.tripweaver.search;

/** Filters for place search. Null means "any". */
public record PlaceQuery(
        String q, String category, String interest, String region, boolean activitiesOnly, int limit, int offset) {

    public PlaceQuery {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be in 1..200");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
    }
}
