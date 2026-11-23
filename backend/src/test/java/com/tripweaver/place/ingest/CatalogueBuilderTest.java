package com.tripweaver.place.ingest;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.place.ingest.OsmExtract.Feature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogueBuilderTest {

    // Boundary: lat 15.0..16.0, lon 73.0..74.0
    static final List<double[][]> BOUNDARY =
            List.<double[][]>of(new double[][] {{15, 73}, {15, 74}, {16, 74}, {16, 73}, {15, 73}});

    static final RegionConfig REGIONS = new RegionConfig(
            new RegionConfig.Dest("goa", "Goa", "Goa", "4"),
            "test",
            List.of(
                    new RegionConfig.RegionDef("north", "North", "Northtown"),
                    new RegionConfig.RegionDef("south", "South", "Southtown")));

    static Feature f(String type, long id, double lat, double lon, String... kv) {
        java.util.Map<String, String> t = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return new Feature(type, id, lat, lon, t);
    }

    static OsmExtract extract(List<Feature> features) {
        return new OsmExtract(
                "test.pbf",
                "2026-11-22T21:20:48Z",
                "Goa",
                BOUNDARY,
                List.of(),
                features,
                List.of(
                        f("node", 900, 15.8, 73.5, "name", "Northtown", "place", "town"),
                        f("node", 901, 15.2, 73.5, "name", "Southtown", "place", "town"),
                        f("node", 902, 15.71, 73.51, "name", "Beachvillage", "place", "village")));
    }

    static BuiltCatalogue build(List<Feature> fs, CuratedData curated) {
        return CatalogueBuilder.build(extract(fs), REGIONS, curated);
    }

    static final CuratedData NONE = new CuratedData("", "2026-11-23T00:00:00Z", List.of());

    @Test
    void acceptsInsideRejectsOutsideBoundary() {
        BuiltCatalogue c = build(
                List.of(
                        f("node", 1, 15.7, 73.5, "name", "Sunny Beach", "natural", "beach"),
                        f("node", 2, 16.5, 73.5, "name", "Far Beach", "natural", "beach")),
                NONE);
        assertEquals(
                List.of("Sunny Beach"),
                c.places().stream().map(BuiltPlace::name).toList());
        assertEquals(Map.of("OUTSIDE_BOUNDARY", 1), c.report().rejected());
        assertEquals(2, c.report().candidates());
        assertEquals(1, c.report().accepted());
    }

    @Test
    void assignsNearestRegionAndNeighbourhood() {
        BuiltCatalogue c = build(
                List.of(
                        f("node", 1, 15.7, 73.5, "name", "Sunny Beach", "natural", "beach"),
                        f("node", 2, 15.3, 73.5, "name", "Fort X", "historic", "fort")),
                NONE);
        BuiltPlace beach = c.places().get(0), fort = c.places().get(1);
        assertEquals("north", beach.regionSlug());
        assertEquals("Beachvillage", beach.neighbourhood());
        assertEquals("south", fort.regionSlug());
        assertNull(fort.neighbourhood(), "nearest settlement more than 3 km away");
        assertEquals(2, c.regions().size());
        assertEquals(15.8, c.regions().get(0).lat(), 1e-9);
    }

    @Test
    void missingRegionAnchorFailsLoudly() {
        RegionConfig bad =
                new RegionConfig(REGIONS.destination(), "", List.of(new RegionConfig.RegionDef("x", "X", "Nowhere")));
        assertThrows(IllegalStateException.class, () -> CatalogueBuilder.build(extract(List.of()), bad, NONE));
    }

    @Test
    void mergesSameNameSameCategoryNearbyPreferringArea() {
        BuiltCatalogue c = build(
                List.of(
                        f("node", 5, 15.7000, 73.5000, "name", "Sunny Beach", "natural", "beach"),
                        f("way", 6, 15.7010, 73.5005, "name", "Sunny  beach", "natural", "beach"),
                        f("node", 7, 15.9000, 73.5000, "name", "Sunny Beach", "natural", "beach")),
                NONE);
        assertEquals(2, c.places().size());
        BuiltPlace kept =
                c.places().stream().filter(p -> p.osmId() == 6).findFirst().orElseThrow();
        assertEquals("way", kept.osmType());
        assertTrue(kept.sources().stream().anyMatch(s -> "node/5".equals(s.sourceRef())));
        assertEquals(1, c.report().merged());
    }

    @Test
    void unknownCostStaysUnknownAndIsReportedMissing() {
        BuiltPlace p = build(List.of(f("node", 1, 15.7, 73.5, "name", "Sunny Beach", "natural", "beach")), NONE)
                .places()
                .getFirst();
        assertTrue(p.costs().isEmpty());
        assertTrue(p.missing().contains("cost"));
        assertTrue(p.missing().contains("opening_hours"));
        assertEquals("PARTIAL", p.quality());
    }

    @Test
    void osmFeeAndOpeningHoursAreProviderSourced() {
        BuiltPlace p = build(
                        List.of(f(
                                "node",
                                1,
                                15.7,
                                73.5,
                                "name",
                                "Museum A",
                                "tourism",
                                "museum",
                                "fee",
                                "no",
                                "opening_hours",
                                "Mo-Sa 10:00-17:00",
                                "description",
                                "Small museum")),
                        NONE)
                .places()
                .getFirst();
        assertEquals(1, p.costs().size());
        assertEquals(0.0, p.costs().getFirst().max());
        assertEquals("PROVIDER_SOURCED", p.costs().getFirst().confidence());
        assertEquals("Mo-Sa 10:00-17:00", p.hours().getFirst().raw());
        assertEquals("OSM", p.hours().getFirst().sourceType());
        assertEquals("COMPLETE", p.quality());
    }

    @Test
    void osmChargeParsedFeeWithoutAmountStaysUnknown() {
        BuiltCatalogue c = build(
                List.of(
                        f("node", 1, 15.7, 73.5, "name", "Fort A", "historic", "fort", "charge", "50 INR"),
                        f("node", 2, 15.7, 73.6, "name", "Fort B", "historic", "fort", "fee", "yes")),
                NONE);
        assertEquals(50.0, c.places().get(0).costs().getFirst().min());
        assertTrue(c.places().get(1).costs().isEmpty());
        assertTrue(c.places().get(1).sources().stream()
                .anyMatch(s -> s.note() != null && s.note().contains("fee=yes")));
    }

    @Test
    void curatedFieldsMatchByOsmRefOrNameAndOverrideOsm() {
        CuratedData cur = new CuratedData(
                "",
                "2026-11-23T00:00:00Z",
                List.of(
                        new CuratedData.Rec(
                                "node/1",
                                "Museum A",
                                new CuratedData.Hours("Mo-Fr 09:00-17:00", "VERIFIED", "https://gov.example/a", "n"),
                                List.of(new CuratedData.Cost(
                                        "ENTRY",
                                        10,
                                        10,
                                        "INR",
                                        "PER_PERSON",
                                        "VERIFIED",
                                        "https://gov.example/a",
                                        null)),
                                null),
                        new CuratedData.Rec(
                                null,
                                "Falls B",
                                null,
                                List.of(),
                                new CuratedData.Seasonal(
                                        "Closed in monsoon",
                                        List.of(6, 7, 8, 9),
                                        "CURATED_ESTIMATE",
                                        "https://gov.example/b",
                                        null)),
                        new CuratedData.Rec("node/999", "Ghost", null, List.of(), null)));
        BuiltCatalogue c = build(
                List.of(
                        f("node", 1, 15.7, 73.5, "name", "Museum A", "tourism", "museum", "opening_hours", "24/7"),
                        f("node", 2, 15.6, 73.5, "name", "Falls B", "waterway", "waterfall")),
                cur);
        BuiltPlace museum = c.places().get(0), falls = c.places().get(1);
        assertEquals(
                List.of("Mo-Fr 09:00-17:00"),
                museum.hours().stream().map(BuiltPlace.Hours::raw).toList());
        assertEquals("VERIFIED", museum.hours().getFirst().confidence());
        assertEquals("https://gov.example/a", museum.costs().getFirst().sourceUrl());
        assertEquals(List.of(6, 7, 8, 9), falls.seasonal().closedMonths());
        assertEquals(List.of("node/999 Ghost"), c.report().unresolvedCurated());
    }

    @Test
    void accommodationIsKeptButNotAnActivity() {
        BuiltPlace p = build(List.of(f("node", 1, 15.7, 73.5, "name", "Stay Inn", "tourism", "guest_house")), NONE)
                .places()
                .getFirst();
        assertFalse(p.mapping().activity());
        assertFalse(p.missing().contains("cost"), "activity fields are not required for stays");
    }

    @Test
    void idsAreDeterministicAndAccessibilityTagged() {
        Feature x = f("node", 42, 15.7, 73.5, "name", "Park", "leisure", "park", "wheelchair", "yes");
        BuiltPlace a = build(List.of(x), NONE).places().getFirst();
        BuiltPlace b = build(List.of(x), NONE).places().getFirst();
        assertEquals(a.id(), b.id());
        assertEquals(java.util.Set.of("wheelchair:yes"), a.accessibility());
    }

    @Test
    void reportCountsByCategoryRegionAndMissingField() {
        BuiltCatalogue c = build(
                List.of(
                        f("node", 1, 15.7, 73.5, "name", "Beach 1", "natural", "beach"),
                        f("node", 2, 15.3, 73.5, "name", "Cafe 2", "amenity", "cafe")),
                NONE);
        assertEquals(Map.of("beach", 1, "cafe", 1), c.report().byPrimaryCategory());
        assertEquals(Map.of("north", 1, "south", 1), c.report().byRegion());
        assertEquals(2, c.report().missingFieldCounts().get("cost"));
        assertEquals(1, c.report().byInterest().get("food"));
    }
}
