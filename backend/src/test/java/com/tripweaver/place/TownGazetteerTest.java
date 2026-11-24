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
}
