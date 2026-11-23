package com.tripweaver.support;

import com.tripweaver.place.ingest.CatalogueImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Imports the bundled real Goa catalogue into the shared test database once. */
@Component
public class TestCatalogue {

    private final CatalogueImportService service;
    private final JdbcTemplate jdbc;

    public TestCatalogue(CatalogueImportService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    public synchronized void ensureGoaImported() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM places", Integer.class);
        if (n != null && n > 0) return;
        service.importFiles(
                Path.of("../data/sample/goa-osm-extract.json"),
                Path.of("../data/curated/goa-regions.json"),
                Path.of("../data/curated/goa-curated.json"),
                null);
    }
}
