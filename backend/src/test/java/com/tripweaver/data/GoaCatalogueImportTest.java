package com.tripweaver.data;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.place.ingest.BuiltCatalogue;
import com.tripweaver.place.ingest.CatalogueImportService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Imports the real bundled Goa extract into the test database, twice, and checks provenance rules. */
@SpringBootTest
class GoaCatalogueImportTest {

    static final Path EXTRACT = Path.of("../data/sample/goa-osm-extract.json");
    static final Path REGIONS = Path.of("../data/curated/goa-regions.json");
    static final Path CURATED = Path.of("../data/curated/goa-curated.json");

    @Autowired
    CatalogueImportService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void importIsReproducibleAndKeepsProvenance() {
        BuiltCatalogue.Report r1 = service.importFiles(EXTRACT, REGIONS, CURATED, null);
        List<UUID> ids1 = jdbc.queryForList("SELECT id FROM places ORDER BY id", UUID.class);
        BuiltCatalogue.Report r2 = service.importFiles(EXTRACT, REGIONS, CURATED, null);
        List<UUID> ids2 = jdbc.queryForList("SELECT id FROM places ORDER BY id", UUID.class);

        assertEquals(r1, r2, "same input, same report");
        assertEquals(ids1, ids2, "stable ids across re-import");
        assertTrue(r1.accepted() > 1000, "expected a real catalogue, got " + r1.accepted());
        assertTrue(r1.rejected().getOrDefault("OUTSIDE_BOUNDARY", 0) > 0, "bbox includes neighbouring states");
        assertEquals(r1.accepted(), jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE is_active", Integer.class));

        // Every place has an OSM provenance record.
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM places p WHERE NOT EXISTS (SELECT 1 FROM place_source_records s WHERE s.place_id = p.id AND s.source_type = 'OSM')",
                        Integer.class));
        // No cost row without a confidence label and source type.
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM place_cost_estimates WHERE confidence IS NULL OR source_type IS NULL",
                        Integer.class));
        // Missing data is visible: activities with no cost are flagged.
        assertTrue(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM places WHERE is_activity AND missing_fields LIKE '%cost%'", Integer.class)
                > 0);
        // Curated official fact landed with its source.
        assertEquals(
                "https://www.incredibleindia.gov.in/en/goa/goa/the-archaeological-museum-old-goa",
                jdbc.queryForObject(
                        "SELECT c.source_url FROM place_cost_estimates c JOIN places p ON p.id = c.place_id WHERE p.osm_type='node' AND p.osm_id=4532503789",
                        String.class));
        // Every active place has a region; Basilica is in Panaji/Old Goa.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE region_id IS NULL", Integer.class));
        assertEquals(
                "panaji-old-goa",
                jdbc.queryForObject(
                        "SELECT r.slug FROM places p JOIN regions r ON r.id = p.region_id WHERE p.osm_type='relation' AND p.osm_id=3569663",
                        String.class));
    }
}
