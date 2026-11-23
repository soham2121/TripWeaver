package com.tripweaver.place.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads the bundled Goa catalogue on startup when the database has no places yet ({@code
 * tripweaver.catalogue.auto-import}), or always when {@code tripweaver.catalogue.reimport=true}.
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

    @Value("${tripweaver.catalogue.extract:../data/sample/goa-osm-extract.json}")
    String extract;

    @Value("${tripweaver.catalogue.regions:../data/curated/goa-regions.json}")
    String regions;

    @Value("${tripweaver.catalogue.curated:../data/curated/goa-curated.json}")
    String curated;

    @Value("${tripweaver.catalogue.report-out:../data/processed/goa-import-report.json}")
    String reportOut;

    public CatalogueImportRunner(CatalogueImportService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImport && !reimport) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM places", Integer.class);
        if (!reimport && count != null && count > 0) return;
        Path ex = Path.of(extract);
        if (!Files.exists(ex)) {
            log.warn("Catalogue extract not found at {}; skipping import", ex.toAbsolutePath());
            return;
        }
        service.importFiles(ex, Path.of(regions), Path.of(curated), Path.of(reportOut));
    }
}
