package com.tripweaver.place;

import com.tripweaver.common.ApiException;
import com.tripweaver.search.PlaceQuery;
import com.tripweaver.search.Suggestion;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    public static final String OSM_ATTRIBUTION = "© OpenStreetMap contributors (ODbL)";

    private final PlaceCatalogue catalogue;
    private final JdbcTemplate jdbc;

    public PlaceController(PlaceCatalogue catalogue, JdbcTemplate jdbc) {
        this.catalogue = catalogue;
        this.jdbc = jdbc;
    }

    public record Page<T>(List<T> items, int limit, int offset, boolean hasMore) {}

    @GetMapping("/search")
    public Page<PlaceSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String interest,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "false") boolean activitiesOnly,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100) throw ApiException.badRequest("BAD_LIMIT", "limit must be in 1..100");
        List<PlaceSummary> rows = catalogue
                .index(destination)
                .search(new PlaceQuery(
                        blank(q), blank(category), blank(interest), blank(region), activitiesOnly, limit + 1, offset));
        boolean more = rows.size() > limit;
        return new Page<>(more ? rows.subList(0, limit) : rows, limit, offset, more);
    }

    @GetMapping("/autocomplete")
    public List<Suggestion> autocomplete(
            @RequestParam String q,
            @RequestParam(required = false) String destination,
            @RequestParam(defaultValue = "8") int limit) {
        if (limit < 1 || limit > 25) throw ApiException.badRequest("BAD_LIMIT", "limit must be in 1..25");
        return catalogue.index(destination).autocomplete(q, limit);
    }

    public record NearbyPlace(PlaceSummary place, double distanceKm) {}

    @GetMapping("/nearby")
    public List<NearbyPlace> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "2") double radiusKm,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String destination,
            @RequestParam(defaultValue = "50") int limit) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180)
            throw ApiException.badRequest("BAD_COORDINATES", "lat/lon out of range");
        if (limit < 1 || limit > 200) throw ApiException.badRequest("BAD_LIMIT", "limit must be in 1..200");
        Set<String> cats = categories == null || categories.isBlank()
                ? Set.of()
                : new HashSet<>(Arrays.asList(categories.split(",")));
        return catalogue.index(destination).nearby(lat, lon, radiusKm, cats, limit).stream()
                .map(n -> new NearbyPlace(n.place(), Math.round(n.distanceKm() * 1000) / 1000.0))
                .toList();
    }

    @GetMapping("/{id}")
    public PlaceDetail get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT p.description, p.website, p.osm_type, p.osm_id, p.seasonal_notes, p.seasonal_closed_months,
                       p.visit_minutes_basis, p.visit_minutes_confidence, d.slug
                FROM places p JOIN destinations d ON d.id = p.destination_id WHERE p.id = ? AND p.is_active
                """, id);
        if (rows.isEmpty()) throw ApiException.notFound("Place", id);
        var r = rows.getFirst();
        PlaceSummary summary = catalogue.index((String) r.get("slug")).get(id);
        if (summary == null) throw ApiException.notFound("Place", id);

        String osmRef = r.get("osm_type") == null ? null : r.get("osm_type") + "/" + r.get("osm_id");
        String months = (String) r.get("seasonal_closed_months");
        PlaceDetail.Seasonal seasonal = r.get("seasonal_notes") == null
                ? null
                : new PlaceDetail.Seasonal(
                        (String) r.get("seasonal_notes"),
                        months == null
                                ? List.of()
                                : Arrays.stream(months.split(","))
                                        .map(Integer::valueOf)
                                        .toList());
        List<String> access = jdbc.queryForList(
                "SELECT tag FROM place_accessibility_tags WHERE place_id = ? ORDER BY tag", String.class, id);
        List<PlaceDetail.Cost> costs = jdbc.query(
                "SELECT kind, amount_min, amount_max, currency, basis, confidence, source_type, source_url, verified_at, note FROM place_cost_estimates WHERE place_id = ? ORDER BY kind",
                (rs, i) -> new PlaceDetail.Cost(
                        rs.getString(1),
                        rs.getDouble(2),
                        rs.getDouble(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getObject(9, OffsetDateTime.class),
                        rs.getString(10)),
                id);
        List<PlaceDetail.Hours> hours = jdbc.query(
                "SELECT raw, confidence, source_type, source_url, verified_at, note FROM place_opening_hours WHERE place_id = ?",
                (rs, i) -> new PlaceDetail.Hours(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class),
                        rs.getString(6)),
                id);
        List<PlaceDetail.Source> sources = jdbc.query(
                "SELECT field_name, source_type, source_ref, source_url, confidence, verified_at, note FROM place_source_records WHERE place_id = ? ORDER BY field_name",
                (rs, i) -> new PlaceDetail.Source(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getObject(6, OffsetDateTime.class),
                        rs.getString(7)),
                id);
        return new PlaceDetail(
                summary,
                (String) r.get("description"),
                (String) r.get("website"),
                osmRef,
                osmRef == null ? null : "https://www.openstreetmap.org/" + osmRef,
                seasonal,
                Set.copyOf(access),
                costs,
                hours,
                sources,
                (String) r.get("visit_minutes_basis"),
                (String) r.get("visit_minutes_confidence"),
                OSM_ATTRIBUTION);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
