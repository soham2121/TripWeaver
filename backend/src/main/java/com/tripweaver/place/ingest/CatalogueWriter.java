package com.tripweaver.place.ingest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists a {@link BuiltCatalogue} idempotently. Place ids are deterministic (derived from the OSM
 * reference), so re-importing updates rows in place and keeps references from trips valid. Places that
 * disappear from the source are deactivated, not deleted.
 */
@Component
public class CatalogueWriter {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public CatalogueWriter(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public UUID write(BuiltCatalogue c) {
        Instant now = Instant.now();
        var d = c.destination();
        int updated = jdbc.update(
                "UPDATE destinations SET name=?, min_lat=?, min_lon=?, max_lat=?, max_lon=? WHERE id=?",
                d.name(),
                d.minLat(),
                d.minLon(),
                d.maxLat(),
                d.maxLon(),
                d.id());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO destinations (id, slug, name, min_lat, min_lon, max_lat, max_lon, created_at) VALUES (?,?,?,?,?,?,?,?)",
                    d.id(),
                    d.slug(),
                    d.name(),
                    d.minLat(),
                    d.minLon(),
                    d.maxLat(),
                    d.maxLon(),
                    ts(now));
        }
        for (var r : c.regions()) {
            int u = jdbc.update(
                    "UPDATE regions SET name=?, center_lat=?, center_lon=? WHERE id=?",
                    r.name(),
                    r.lat(),
                    r.lon(),
                    r.id());
            if (u == 0) {
                jdbc.update(
                        "INSERT INTO regions (id, destination_id, slug, name, center_lat, center_lon) VALUES (?,?,?,?,?,?)",
                        r.id(),
                        d.id(),
                        r.slug(),
                        r.name(),
                        r.lat(),
                        r.lon());
            }
        }

        var rep = c.report();
        UUID batchId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO place_import_batches (id, destination_id, source, source_file, source_timestamp, imported_at, accepted, rejected, merged, report_json) VALUES (?,?,?,?,?,?,?,?,?,?)",
                batchId,
                d.id(),
                "OSM+CURATED",
                rep.sourceFile(),
                rep.sourceTimestamp() == null ? null : ts(Instant.parse(rep.sourceTimestamp())),
                ts(now),
                rep.accepted(),
                rep.rejected().values().stream().mapToInt(Integer::intValue).sum(),
                rep.merged(),
                json.writeValueAsString(rep));

        Set<UUID> existing =
                new HashSet<>(jdbc.queryForList("SELECT id FROM places WHERE destination_id = ?", UUID.class, d.id()));
        for (String child : List.of(
                "place_categories",
                "place_interest_tags",
                "place_accessibility_tags",
                "place_source_records",
                "place_cost_estimates",
                "place_opening_hours")) {
            jdbc.update(
                    "DELETE FROM " + child + " WHERE place_id IN (SELECT id FROM places WHERE destination_id = ?)",
                    d.id());
        }

        List<Object[]> inserts = new ArrayList<>(), updates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (BuiltPlace p : c.places()) {
            seen.add(p.id());
            UUID regionId = c.regions().stream()
                    .filter(r -> r.slug().equals(p.regionSlug()))
                    .map(BuiltCatalogue.Region::id)
                    .findFirst()
                    .orElse(null);
            var m = p.mapping();
            String months = p.seasonal() == null || p.seasonal().closedMonths() == null
                    ? null
                    : String.join(
                            ",",
                            p.seasonal().closedMonths().stream()
                                    .map(String::valueOf)
                                    .toList());
            Object[] common = {
                regionId,
                p.name(),
                p.description(),
                p.lat(),
                p.lon(),
                p.neighbourhood(),
                m.primaryCategory(),
                m.activity(),
                m.activity() ? m.visitMinutesMin() : null,
                m.activity() ? m.visitMinutesMax() : null,
                m.activity() ? "TripWeaver category default for " + m.primaryCategory() : null,
                m.activity() ? "CURATED_ESTIMATE" : "UNKNOWN",
                m.intensity(),
                p.seasonal() == null ? null : p.seasonal().text(),
                months,
                truncate(p.website(), 500),
                json.writeValueAsString(p.osmTags()),
                p.quality(),
                String.join(",", p.missing()),
                batchId,
                ts(now)
            };
            if (existing.contains(p.id())) {
                Object[] row = new Object[common.length + 1];
                System.arraycopy(common, 0, row, 0, common.length);
                row[common.length] = p.id();
                updates.add(row);
            } else {
                Object[] row = new Object[common.length + 5];
                row[0] = p.id();
                row[1] = d.id();
                row[2] = p.osmType();
                row[3] = p.osmId();
                System.arraycopy(common, 0, row, 4, common.length);
                row[row.length - 1] = ts(now);
                inserts.add(row);
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO places (id, destination_id, osm_type, osm_id, region_id, name, description, latitude, longitude, neighbourhood, primary_category, is_activity, visit_minutes_min, visit_minutes_max, visit_minutes_basis, visit_minutes_confidence, activity_intensity, seasonal_notes, seasonal_closed_months, website, osm_tags_json, data_quality_status, missing_fields, import_batch_id, updated_at, is_active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?)",
                inserts);
        jdbc.batchUpdate(
                "UPDATE places SET region_id=?, name=?, description=?, latitude=?, longitude=?, neighbourhood=?, primary_category=?, is_activity=?, visit_minutes_min=?, visit_minutes_max=?, visit_minutes_basis=?, visit_minutes_confidence=?, activity_intensity=?, seasonal_notes=?, seasonal_closed_months=?, website=?, osm_tags_json=?, data_quality_status=?, missing_fields=?, import_batch_id=?, updated_at=?, is_active=TRUE WHERE id=?",
                updates);
        List<Object[]> deactivate = new ArrayList<>();
        for (UUID id : existing) if (!seen.contains(id)) deactivate.add(new Object[] {ts(now), id});
        jdbc.batchUpdate(
                "UPDATE places SET is_active=FALSE, updated_at=? WHERE id=? AND osm_id IS NOT NULL", deactivate);

        List<Object[]> cats = new ArrayList<>(), tags = new ArrayList<>(), acc = new ArrayList<>();
        List<Object[]> srcs = new ArrayList<>(), costs = new ArrayList<>(), hours = new ArrayList<>();
        for (BuiltPlace p : c.places()) {
            for (String s : p.mapping().categories()) cats.add(new Object[] {p.id(), s});
            for (String s : p.mapping().interests()) tags.add(new Object[] {p.id(), s});
            for (String s : p.accessibility()) acc.add(new Object[] {p.id(), s});
            for (var s : p.sources()) {
                srcs.add(new Object[] {
                    UUID.randomUUID(),
                    p.id(),
                    s.field(),
                    s.sourceType(),
                    s.sourceRef(),
                    s.sourceUrl(),
                    s.confidence(),
                    ts(s.verifiedAt()),
                    s.note()
                });
            }
            for (var k : p.costs()) {
                costs.add(new Object[] {
                    UUID.randomUUID(),
                    p.id(),
                    k.kind(),
                    k.min(),
                    k.max(),
                    k.currency(),
                    k.basis(),
                    k.confidence(),
                    k.sourceType(),
                    k.sourceUrl(),
                    ts(k.verifiedAt()),
                    k.note()
                });
            }
            for (var h : p.hours()) {
                hours.add(new Object[] {
                    UUID.randomUUID(),
                    p.id(),
                    truncate(h.raw(), 500),
                    h.confidence(),
                    h.sourceType(),
                    h.sourceUrl(),
                    ts(h.verifiedAt()),
                    h.note()
                });
            }
        }
        jdbc.batchUpdate("INSERT INTO place_categories (place_id, category) VALUES (?,?)", cats);
        jdbc.batchUpdate("INSERT INTO place_interest_tags (place_id, tag) VALUES (?,?)", tags);
        jdbc.batchUpdate("INSERT INTO place_accessibility_tags (place_id, tag) VALUES (?,?)", acc);
        jdbc.batchUpdate(
                "INSERT INTO place_source_records (id, place_id, field_name, source_type, source_ref, source_url, confidence, verified_at, note) VALUES (?,?,?,?,?,?,?,?,?)",
                srcs);
        jdbc.batchUpdate(
                "INSERT INTO place_cost_estimates (id, place_id, kind, amount_min, amount_max, currency, basis, confidence, source_type, source_url, verified_at, note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                costs);
        jdbc.batchUpdate(
                "INSERT INTO place_opening_hours (id, place_id, raw, confidence, source_type, source_url, verified_at, note) VALUES (?,?,?,?,?,?,?,?)",
                hours);
        return batchId;
    }

    private static OffsetDateTime ts(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
