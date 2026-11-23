package com.tripweaver.search;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.place.PlaceSummary;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceSearchIndexTest {

    static PlaceSummary p(
            String name, String cat, Set<String> interests, String region, String hood, double lat, double lon) {
        return new PlaceSummary(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                lat,
                lon,
                cat,
                Set.of(cat),
                interests,
                region,
                region + " name",
                hood,
                !cat.equals("accommodation"),
                30,
                60,
                "LOW",
                List.of(),
                false,
                "PARTIAL",
                List.of("cost"),
                Set.of(),
                List.of());
    }

    static final List<PlaceSummary> PLACES = List.of(
            p("Fort Aguada", "fort", Set.of("heritage"), "north-coast", "Candolim", 15.4920, 73.7740),
            p("Aguada Beach", "beach", Set.of("beach"), "north-coast", "Candolim", 15.4988, 73.7671),
            p("Baga Beach", "beach", Set.of("beach"), "north-coast", "Baga", 15.5560, 73.7516),
            p("Britto's", "restaurant", Set.of("food"), "north-coast", "Baga", 15.5570, 73.7520),
            p(
                    "Basilica of Bom Jesus",
                    "religious_site",
                    Set.of("spiritual", "heritage"),
                    "panaji-old-goa",
                    "Old Goa",
                    15.5007,
                    73.9114),
            p("Palolem Beach", "beach", Set.of("beach"), "canacona", "Palolem", 15.0100, 74.0232),
            p("Baga Stay", "accommodation", Set.of(), "north-coast", "Baga", 15.5550, 73.7530));

    static final PlaceSearchIndex IDX = PlaceSearchIndex.build(PLACES);

    static List<String> names(List<PlaceSummary> ps) {
        return ps.stream().map(PlaceSummary::name).toList();
    }

    @Test
    void searchMatchesFullNameAndLaterWords() {
        assertEquals(
                List.of("Aguada Beach", "Fort Aguada"),
                names(IDX.search(new PlaceQuery("aguada", null, null, null, false, 20, 0))));
    }

    @Test
    void searchFiltersByCategoryInterestRegionAndActivity() {
        assertEquals(
                List.of("Baga Beach"), names(IDX.search(new PlaceQuery("baga", "beach", null, null, false, 20, 0))));
        assertEquals(
                List.of("Basilica of Bom Jesus", "Fort Aguada"),
                names(IDX.search(new PlaceQuery(null, null, "heritage", null, false, 20, 0))));
        assertEquals(
                List.of("Palolem Beach"),
                names(IDX.search(new PlaceQuery(null, null, null, "canacona", false, 20, 0))));
        assertEquals(List.of("Baga Beach"), names(IDX.search(new PlaceQuery("baga", null, null, null, true, 20, 0))));
    }

    @Test
    void searchPaginatesInNameOrder() {
        List<PlaceSummary> page1 = IDX.search(new PlaceQuery(null, "beach", null, null, false, 2, 0));
        List<PlaceSummary> page2 = IDX.search(new PlaceQuery(null, "beach", null, null, false, 2, 2));
        assertEquals(List.of("Aguada Beach", "Baga Beach"), names(page1));
        assertEquals(List.of("Palolem Beach"), names(page2));
    }

    @Test
    void autocompleteSuggestsPlacesAndFacetsWithoutDuplicates() {
        List<Suggestion> s = IDX.autocomplete("bag", 10);
        assertEquals(
                List.of("NEIGHBOURHOOD", "PLACE", "PLACE"),
                s.stream().map(Suggestion::type).toList());
        assertEquals("Baga", s.getFirst().label());
        List<Suggestion> b = IDX.autocomplete("beach", 10);
        assertTrue(b.stream()
                .anyMatch(x -> x.type().equals("INTEREST") && x.value().equals("beach")));
        assertEquals(
                1,
                IDX.autocomplete("aguada", 10).stream()
                        .filter(x -> x.label().equals("Fort Aguada"))
                        .count());
        assertEquals(1, IDX.autocomplete("bag", 1).size());
        assertTrue(IDX.autocomplete("", 10).isEmpty());
    }

    @Test
    void nearbyReturnsWithinRadiusSortedByDistance() {
        List<PlaceSearchIndex.Nearby> n = IDX.nearby(15.556, 73.7516, 1.0, Set.of(), 10);
        assertEquals(
                List.of("Baga Beach", "Britto's", "Baga Stay"),
                n.stream().map(x -> x.place().name()).toList());
        assertTrue(n.get(0).distanceKm() <= n.get(1).distanceKm());
        assertEquals(
                List.of("Britto's"),
                IDX.nearby(15.556, 73.7516, 1.0, Set.of("restaurant"), 10).stream()
                        .map(x -> x.place().name())
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> IDX.nearby(15, 73, -1, Set.of(), 5));
    }

    @Test
    void indexStatsReportTermCounts() {
        assertEquals(7, IDX.placeCount());
        assertTrue(IDX.indexedTerms() > 7);
    }
}
