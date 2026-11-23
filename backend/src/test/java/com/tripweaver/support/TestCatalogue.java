package com.tripweaver.support;

import com.tripweaver.place.ingest.CatalogueImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Imports the bundled real catalogues into the shared test database once per destination. */
@Component
public class TestCatalogue {

    private final CatalogueImportService service;
    private final JdbcTemplate jdbc;

    public TestCatalogue(CatalogueImportService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    public void ensureGoaImported() {
        ensureImported("goa");
    }

    public synchronized void ensureImported(String slug) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM places p JOIN destinations d ON d.id = p.destination_id WHERE d.slug = ?",
                Integer.class,
                slug);
        if (n != null && n > 0) return;
        service.importFiles(
                Path.of("../data/sample/" + slug + "-osm-extract.json"),
                Path.of("../data/curated/" + slug + "-regions.json"),
                Path.of("../data/curated/" + slug + "-curated.json"),
                null);
    }
}
