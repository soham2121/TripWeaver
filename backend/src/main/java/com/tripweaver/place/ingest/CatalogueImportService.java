package com.tripweaver.place.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Reads the extract and curated files, builds the catalogue, writes it and the import report. */
@Service
public class CatalogueImportService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueImportService.class);

    private final CatalogueWriter writer;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;

    public CatalogueImportService(CatalogueWriter writer, JsonMapper json, ApplicationEventPublisher events) {
        this.writer = writer;
        this.json = json;
        this.events = events;
    }

    public BuiltCatalogue.Report importFiles(Path extract, Path regions, Path curated, Path reportOut) {
        OsmExtract ex = json.readValue(extract.toFile(), OsmExtract.class);
        RegionConfig rc = json.readValue(regions.toFile(), RegionConfig.class);
        CuratedData cd = json.readValue(curated.toFile(), CuratedData.class);
        BuiltCatalogue built = CatalogueBuilder.build(ex, rc, cd);
        writer.write(built);
        events.publishEvent(new CatalogueImportedEvent(rc.destination().slug()));
        BuiltCatalogue.Report r = built.report();
        if (reportOut != null) {
            try {
                Files.createDirectories(reportOut.toAbsolutePath().getParent());
                json.writerWithDefaultPrettyPrinter().writeValue(reportOut.toFile(), r);
            } catch (java.io.IOException e) {
                log.warn("Could not write import report to {}: {}", reportOut, e.getMessage());
            }
        }
        log.info(
                "Catalogue import {}: candidates={} accepted={} merged={} rejected={} unresolvedCurated={}",
                rc.destination().slug(),
                r.candidates(),
                r.accepted(),
                r.merged(),
                r.rejected(),
                r.unresolvedCurated());
        return r;
    }
}
