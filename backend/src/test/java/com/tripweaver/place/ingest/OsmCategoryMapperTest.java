package com.tripweaver.place.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OsmCategoryMapperTest {

    static CategoryMapping map(String... kv) {
        java.util.Map<String, String> tags = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) tags.put(kv[i], kv[i + 1]);
        return OsmCategoryMapper.map(tags).orElse(null);
    }

    @Test
    void unnamedFeaturesAreIgnored() {
        assertNull(map("natural", "beach"));
        assertFalse(OsmCategoryMapper.isCandidate(Map.of("natural", "beach")));
    }

    @Test
    void beachMapsToBeachInterest() {
        CategoryMapping m = map("name", "Baga Beach", "natural", "beach");
        assertEquals("beach", m.primaryCategory());
        assertEquals(Set.of("beach"), m.interests());
        assertTrue(m.activity());
        assertTrue(m.visitMinutesMin() > 0 && m.visitMinutesMax() >= m.visitMinutesMin());
    }

    @Test
    void fortIsHeritage() {
        assertEquals("fort", map("name", "Fort Aguada", "historic", "fort").primaryCategory());
        assertTrue(map("name", "Chapora Fort", "historic", "castle").interests().contains("heritage"));
    }

    @Test
    void restaurantGetsCuisineCategories() {
        CategoryMapping m = map("name", "Gunpowder", "amenity", "restaurant", "cuisine", "goan;Seafood");
        assertEquals("restaurant", m.primaryCategory());
        assertEquals(Set.of("food"), m.interests());
        assertTrue(m.categories().containsAll(Set.of("restaurant", "cuisine:goan", "cuisine:seafood")));
    }

    @Test
    void worshipPlacesNeedNotabilitySignal() {
        assertNull(map("name", "Small Chapel", "amenity", "place_of_worship"));
        CategoryMapping m = map("name", "Basilica of Bom Jesus", "amenity", "place_of_worship", "wikidata", "Q1");
        assertEquals("religious_site", m.primaryCategory());
        assertEquals(Set.of("spiritual", "heritage"), m.interests());
    }

    @Test
    void accommodationIsNotAnActivity() {
        CategoryMapping m = map("name", "Some Resort", "tourism", "resort");
        assertEquals("accommodation", m.primaryCategory());
        assertFalse(m.activity());
    }

    @Test
    void nightlifeWaterSportsAndNature() {
        assertEquals(
                Set.of("nightlife"), map("name", "Club", "amenity", "nightclub").interests());
        assertTrue(map("name", "Dive Goa", "amenity", "dive_centre").interests().contains("water_sports"));
        assertTrue(map("name", "Dudhsagar Falls", "waterway", "waterfall")
                .interests()
                .contains("nature"));
        assertTrue(map("name", "Bhagwan Mahavir", "boundary", "protected_area")
                .interests()
                .contains("wildlife"));
    }

    @Test
    void irrelevantFeaturesAreIgnored() {
        assertNull(map("name", "Bank", "amenity", "bank"));
        assertNull(map("name", "Road", "highway", "primary"));
    }

    @Test
    void settlementDetection() {
        assertTrue(OsmCategoryMapper.isSettlement(Map.of("place", "village", "name", "Candolim")));
        assertFalse(OsmCategoryMapper.isSettlement(Map.of("place", "island", "name", "Divar")));
        assertFalse(OsmCategoryMapper.isSettlement(Map.of("place", "town")));
        assertFalse(OsmCategoryMapper.isSettlement(Map.of("name", "Shop")), "named node without place tag");
    }
}
