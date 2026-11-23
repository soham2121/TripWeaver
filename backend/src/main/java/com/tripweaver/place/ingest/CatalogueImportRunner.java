package com.tripweaver.place.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads each bundled destination catalogue on startup when it has no places yet ({@code
 * tripweaver.catalogue.auto-import}), or always when {@code tripweaver.catalogue.reimport=true}. Files follow the
 * convention {@code <data-dir>/sample/<slug>-osm-extract.json}, {@code curated/<slug>-regions.json},
 * {@code curated/<slug>-curated.json}; the report goes to {@code processed/<slug>-import-report.json}.
 */
@Component
public class CatalogueImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogueImportRunner.class);

    private final CatalogueImportService service;
    private final JdbcTemplate jdbc;

    @Value("${tripweaver.catalogue.auto-import:true}")
    boolean autoImport;

    @Value("${tripweaver.catalogue.reimport:false}")
    boolean reimport;

    @Value("${tripweaver.catalogue.destinations:goa,konkan}")
    List<String> destinations;

    @Value("${tripweaver.catalogue.data-dir:../data}")
    String dataDir;

    public CatalogueImportRunner(CatalogueImportService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImport && !reimport) return;
        for (String slug : destinations) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM places p JOIN destinations d ON d.id = p.destination_id WHERE d.slug = ?",
                    Integer.class,
                    slug);
            if (!reimport && count != null && count > 0) continue;
            Path dir = Path.of(dataDir);
            Path ex = dir.resolve("sample/" + slug + "-osm-extract.json");
            if (!Files.exists(ex)) {
                log.warn("Catalogue extract not found at {}; skipping {}", ex.toAbsolutePath(), slug);
                continue;
            }
            service.importFiles(
                    ex,
                    dir.resolve("curated/" + slug + "-regions.json"),
                    dir.resolve("curated/" + slug + "-curated.json"),
                    dir.resolve("processed/" + slug + "-import-report.json"));
        }
    }
}
