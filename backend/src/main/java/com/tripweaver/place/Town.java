package com.tripweaver.place;

import java.util.List;

/**
 * A city or town from the OpenStreetMap gazetteer, used as a searchable location and as a road-trip stay.
 *
 * @param kind OSM {@code place} value: city or town (gazetteer), suburb or village (search-only localities)
 * @param aliases other Latin-script names (name:en, alt_name, old_name), e.g. Alibaug for Alibag
 */
public record Town(String name, String kind, double lat, double lon, List<String> aliases) {

    /** Stable key for requests: name plus rounded coordinates (names repeat across states). */
    public String key() {
        return String.format(java.util.Locale.ROOT, "%s@%.3f,%.3f", name, lat, lon);
    }
}
