package com.tripweaver.place;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.search.Suggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownGazetteerTest {

    final TownGazetteer g = new TownGazetteer(List.of(
            new Town("Alibag", "town", 18.65, 72.877, List.of("Alibaug")),
            new Town("Kolhapur", "city", 16.703, 74.241, List.of()),
            new Town("Kolad", "town", 18.41, 73.31, List.of())));

    @Test
    void findsTownsByNameOrAliasWithCoordinatesCitiesFirst() {
        Suggestion s = g.autocomplete("alibau", 5).getFirst();
        assertEquals("TOWN", s.type());
        assertEquals("Alibag", s.label());
        assertEquals(18.65, s.lat());
        assertEquals(
                List.of("Kolhapur", "Kolad"),
                g.autocomplete("kol", 5).stream().map(Suggestion::label).toList());
        assertTrue(g.autocomplete("xyz", 5).isEmpty());
    }

    @Test
    void villagesAndSuburbsAreSearchableButNotStays() {
        TownGazetteer withLocal = new TownGazetteer(
                List.of(new Town("Calangute", "town", 15.546, 73.765, List.of())),
                List.of(
                        new Town("Colvale", "suburb", 15.644, 73.837, List.of()),
                        new Town("Colva", "village", 15.277, 73.920, List.of()),
                        new Town("Candolim", "suburb", 15.520, 73.766, List.of())));
        assertEquals(
                List.of("Colva", "Colvale"),
                withLocal.autocomplete("colva", 5).stream()
                        .map(Suggestion::label)
                        .toList()); // exact name first
        Suggestion c = withLocal.autocomplete("candolim", 5).getFirst();
        assertEquals("TOWN", c.type());
        assertEquals(15.520, c.lat());
        assertEquals(
                List.of("Calangute"), withLocal.all().stream().map(Town::name).toList());
    }

    @Test
    void localitiesLoadFromTheBundledGoaExtract() {
        List<Town> l = TownGazetteer.localities(java.nio.file.Path.of("../data/sample"));
        assertTrue(l.stream().anyMatch(t -> t.name().equals("Candolim")));
        assertTrue(l.stream().anyMatch(t -> t.name().equals("Colva")));
        assertTrue(l.stream().allMatch(t -> TownGazetteer.LOCALITY_KINDS.contains(t.kind())));
    }
}
