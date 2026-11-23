package com.tripweaver.place.ingest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps OSM tags to TripWeaver categories, interest tags, intensity and default visit duration.
 *
 * <p>Rules are checked in order; the first match wins. Features without a {@code name} are ignored.
 * Places of worship are kept only with a notability signal (historic, heritage, wikidata, wikipedia or
 * tourism=attraction) because Goa has thousands of small chapels and shrines.
 *
 * <p>Visit durations are TripWeaver category defaults (curated estimates for planning), labelled as such
 * wherever shown.
 */
public final class OsmCategoryMapper {

    /** Interest taxonomy shared by the recommender. */
    public static final List<String> INTERESTS = List.of(
            "beach",
            "food",
            "nightlife",
            "heritage",
            "culture",
            "spiritual",
            "nature",
            "viewpoint",
            "adventure",
            "water_sports",
            "wildlife",
            "shopping",
            "family",
            "sightseeing");

    private static final Set<String> SETTLEMENTS =
            Set.of("city", "town", "village", "suburb", "neighbourhood", "hamlet");
    private static final Set<String> STAYS = Set.of("hotel", "guest_house", "hostel", "resort", "motel", "apartment");
    private static final Set<String> HERITAGE_HISTORIC = Set.of(
            "monument",
            "memorial",
            "archaeological_site",
            "ruins",
            "building",
            "church",
            "temple",
            "manor",
            "city_gate",
            "tower",
            "palace");
    private static final Set<String> WATER_SPORTS =
            Set.of("scuba_diving", "surfing", "kitesurfing", "water_ski", "canoe", "kayak", "parasailing", "sailing");

    private OsmCategoryMapper() {}

    public static boolean isSettlement(Map<String, String> tags) {
        return tags.containsKey("name") && SETTLEMENTS.contains(tags.getOrDefault("place", ""));
    }

    public static boolean isCandidate(Map<String, String> tags) {
        return map(tags).isPresent();
    }

    public static Optional<CategoryMapping> map(Map<String, String> t) {
        if (isBlank(t.get("name"))) return Optional.empty();
        String tourism = t.getOrDefault("tourism", "");
        String amenity = t.getOrDefault("amenity", "");
        String historic = t.getOrDefault("historic", "");
        String natural = t.getOrDefault("natural", "");
        String leisure = t.getOrDefault("leisure", "");
        String boundary = t.getOrDefault("boundary", "");

        if (STAYS.contains(tourism)) return m("accommodation", Set.of(), false, "LOW", 0, 0, t);
        if (natural.equals("beach")) return m("beach", Set.of("beach"), true, "LOW", 90, 180, t);
        if (historic.equals("fort") || historic.equals("castle"))
            return m("fort", Set.of("heritage", "viewpoint"), true, "MODERATE", 45, 90, t);
        if (tourism.equals("museum")) return m("museum", Set.of("culture", "heritage"), true, "LOW", 60, 120, t);
        if (tourism.equals("gallery")) return m("gallery", Set.of("culture"), true, "LOW", 30, 60, t);
        if (amenity.equals("place_of_worship")) {
            boolean notable = !historic.isEmpty()
                    || t.containsKey("heritage")
                    || t.containsKey("wikidata")
                    || t.containsKey("wikipedia")
                    || tourism.equals("attraction");
            return notable
                    ? m("religious_site", Set.of("spiritual", "heritage"), true, "LOW", 20, 45, t)
                    : Optional.empty();
        }
        if (HERITAGE_HISTORIC.contains(historic)) return m("heritage_site", Set.of("heritage"), true, "LOW", 20, 60, t);
        if (tourism.equals("viewpoint")) return m("viewpoint", Set.of("viewpoint", "nature"), true, "LOW", 20, 45, t);
        if ("waterfall".equals(t.get("waterway")) || natural.equals("waterfall"))
            return m("waterfall", Set.of("nature", "adventure"), true, "MODERATE", 120, 240, t);
        if (natural.equals("peak")) return m("peak", Set.of("nature", "adventure"), true, "HIGH", 120, 240, t);
        if (natural.equals("cave_entrance"))
            return m("cave", Set.of("nature", "adventure"), true, "MODERATE", 45, 90, t);
        if (leisure.equals("nature_reserve") || boundary.equals("protected_area") || boundary.equals("national_park"))
            return m("nature_reserve", Set.of("nature", "wildlife"), true, "MODERATE", 120, 240, t);
        if (tourism.equals("zoo") || tourism.equals("aquarium"))
            return m(tourism, Set.of("wildlife", "family"), true, "LOW", 90, 180, t);
        if (leisure.equals("water_park"))
            return m("water_park", Set.of("water_sports", "family", "adventure"), true, "MODERATE", 180, 300, t);
        if (tourism.equals("theme_park"))
            return m("theme_park", Set.of("family", "adventure"), true, "MODERATE", 180, 300, t);
        if (amenity.equals("dive_centre")
                || "scuba_diving".equals(t.get("shop"))
                || WATER_SPORTS.contains(t.getOrDefault("sport", "")))
            return m("water_sports", Set.of("water_sports", "adventure"), true, "HIGH", 120, 240, t);
        if (leisure.equals("park") || leisure.equals("garden"))
            return m("park", Set.of("nature", "family"), true, "LOW", 30, 90, t);
        if (amenity.equals("nightclub")) return m("nightclub", Set.of("nightlife"), true, "LOW", 120, 240, t);
        if (amenity.equals("bar") || amenity.equals("pub"))
            return m("bar", Set.of("nightlife"), true, "LOW", 60, 150, t);
        if (amenity.equals("restaurant")) return m("restaurant", Set.of("food"), true, "LOW", 60, 90, t);
        if (amenity.equals("cafe")) return m("cafe", Set.of("food"), true, "LOW", 30, 60, t);
        if (amenity.equals("fast_food")) return m("fast_food", Set.of("food"), true, "LOW", 20, 45, t);
        if (amenity.equals("marketplace") || "mall".equals(t.get("shop")))
            return m("market", Set.of("shopping"), true, "LOW", 60, 120, t);
        if (tourism.equals("camp_site")) return m("campsite", Set.of("nature"), true, "LOW", 60, 180, t);
        if (tourism.equals("attraction")) return m("attraction", Set.of("sightseeing"), true, "LOW", 30, 90, t);
        return Optional.empty();
    }

    private static Optional<CategoryMapping> m(
            String primary,
            Set<String> interests,
            boolean activity,
            String intensity,
            int min,
            int max,
            Map<String, String> t) {
        Set<String> cats = new LinkedHashSet<>();
        cats.add(primary);
        String cuisine = t.get("cuisine");
        if (cuisine != null) {
            for (String c : cuisine.split("[;,]")) {
                String n = c.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (!n.isEmpty()) cats.add("cuisine:" + n);
            }
        }
        return Optional.of(new CategoryMapping(primary, Set.copyOf(cats), interests, activity, intensity, min, max));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
