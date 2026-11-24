package com.tripweaver.place;

import com.tripweaver.common.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public DestinationController(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Region(String slug, String name, double lat, double lon) {}

    public record Destination(
            String slug, String name, double[] bbox, int placeCount, int activityCount, List<Region> regions) {}

    @GetMapping
    public List<Destination> list() {
        return jdbc.query(
                """
                SELECT d.id, d.slug, d.name, d.min_lat, d.min_lon, d.max_lat, d.max_lon,
                  (SELECT COUNT(*) FROM places p WHERE p.destination_id = d.id AND p.is_active),
                  (SELECT COUNT(*) FROM places p WHERE p.destination_id = d.id AND p.is_active AND p.is_activity)
                FROM destinations d ORDER BY d.name
                """,
                (rs, i) -> new Destination(
                        rs.getString(2),
                        rs.getString(3),
                        new double[] {rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)},
                        rs.getInt(8),
                        rs.getInt(9),
                        jdbc.query(
                                "SELECT slug, name, center_lat, center_lon FROM regions WHERE destination_id = ? ORDER BY name",
                                (r2, j) ->
                                        new Region(r2.getString(1), r2.getString(2), r2.getDouble(3), r2.getDouble(4)),
                                rs.getObject(1))));
    }

    /** Latest import report for the destination: counts per category/region/interest and missing-data gaps. */
    @GetMapping("/{slug}/coverage")
    public Map<String, Object> coverage(@PathVariable String slug) {
        var rows = jdbc.queryForList("""
                SELECT b.report_json, b.imported_at, b.source_file, b.source_timestamp
                FROM place_import_batches b JOIN destinations d ON d.id = b.destination_id
                WHERE d.slug = ? ORDER BY b.imported_at DESC LIMIT 1
                """, slug);
        if (rows.isEmpty()) throw ApiException.notFound("Destination coverage", slug);
        var r = rows.getFirst();
        JsonNode report = json.readTree((String) r.get("report_json"));
        // Live counts from the catalogue as stored now (not the import report): region -> category -> active places.
        Map<String, Map<String, Integer>> live = new java.util.TreeMap<>();
        jdbc.query(
                """
                SELECT COALESCE(r.slug, '(none)'), p.primary_category, COUNT(*)
                FROM places p JOIN destinations d ON d.id = p.destination_id LEFT JOIN regions r ON r.id = p.region_id
                WHERE d.slug = ? AND p.is_active GROUP BY 1, 2
                """,
                rs -> {
                    live.computeIfAbsent(rs.getString(1), k -> new java.util.TreeMap<>())
                            .put(rs.getString(2), rs.getInt(3));
                },
                slug);
        return Map.of(
                "destination",
                slug,
                "importedAt",
                String.valueOf(r.get("imported_at")),
                "sourceFile",
                r.get("source_file"),
                "attribution",
                PlaceController.OSM_ATTRIBUTION,
                "report",
                report,
                "byRegionAndCategory",
                live);
    }
}
