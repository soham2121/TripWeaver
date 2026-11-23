package com.tripweaver.data;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.place.ingest.BuiltCatalogue;
import com.tripweaver.place.ingest.CatalogueImportService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** The Mumbai–Goa corridor is a second destination built by the same pipeline (no algorithm changes). */
@SpringBootTest
class KonkanCatalogueImportTest {

    @Autowired
    CatalogueImportService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void corridorImportsAsItsOwnDestinationWithAccommodationAndAttractions() {
        BuiltCatalogue.Report r = service.importFiles(
                Path.of("../data/sample/konkan-osm-extract.json"),
                Path.of("../data/curated/konkan-regions.json"),
                Path.of("../data/curated/konkan-curated.json"),
                null);
        assertTrue(r.accepted() > 1000, "accepted " + r.accepted());
        assertTrue(r.rejected().getOrDefault("OUTSIDE_BOUNDARY", 0) > 0, "bbox reaches into Goa and Karnataka");
        String inKonkan = " FROM places p JOIN destinations d ON d.id = p.destination_id WHERE d.slug = 'konkan'";
        assertEquals(
                12,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM regions r JOIN destinations d ON d.id = r.destination_id WHERE d.slug = 'konkan'",
                        Integer.class));
        assertTrue(jdbc.queryForObject(
                        "SELECT COUNT(*)" + inKonkan + " AND p.primary_category = 'accommodation'", Integer.class)
                > 100);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*)" + inKonkan + " AND p.is_activity", Integer.class) > 500);
        // Nothing south of the Goa border (≈15.8° N at the coast) belongs to the corridor's Goa side.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*)" + inKonkan + " AND p.latitude < 15.6", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*)" + inKonkan + " AND p.region_id IS NULL", Integer.class));
    }
}
