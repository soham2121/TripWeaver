package com.tripweaver.place;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Radius queries on the PostGIS {@code places.geog} column (GiST index). Returns candidate ids only; ranking,
 * selection and ordering stay in the Java algorithms. On a database without PostGIS (H2 tests) {@link
 * #available()} is false and callers use the in-memory index instead.
 */
@Component
public class SpatialPlaces {

    private static final Logger log = LoggerFactory.getLogger(SpatialPlaces.class);

    public record Hit(UUID id, double distanceKm) {}

    private final JdbcTemplate jdbc;
    private volatile Boolean available;

    public SpatialPlaces(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean available() {
        Boolean a = available;
        if (a == null) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = 'places' AND LOWER(column_name) = 'geog'",
                    Integer.class);
            available = a = n != null && n > 0;
            log.info("PostGIS nearby queries {}", a ? "enabled" : "unavailable; using the in-memory index");
        }
        return a;
    }

    /** Active places of a destination within radiusKm of (lat, lon), nearest first. Categories: any match. */
    public List<Hit> within(
            String destination, double lat, double lon, double radiusKm, Set<String> categories, int limit) {
        List<Object> args = new ArrayList<>(List.of(lon, lat, destination, radiusKm * 1000));
        String catFilter = "";
        if (!categories.isEmpty()) {
            catFilter = " AND EXISTS (SELECT 1 FROM place_categories c WHERE c.place_id = p.id AND c.category IN ("
                    + String.join(",", categories.stream().map(c -> "?").toList()) + "))";
            args.addAll(categories);
        }
        args.add(limit);
        return jdbc.query(
                """
                SELECT p.id, extensions.ST_Distance(p.geog, q.g) AS m
                FROM places p
                JOIN destinations d ON d.id = p.destination_id
                CROSS JOIN (SELECT extensions.ST_SetSRID(extensions.ST_MakePoint(?, ?), 4326)::extensions.geography AS g) q
                WHERE d.slug = ? AND p.is_active AND extensions.ST_DWithin(p.geog, q.g, ?)
                """ + catFilter + " ORDER BY m, p.id LIMIT ?",
                (rs, i) -> new Hit(rs.getObject(1, UUID.class), rs.getDouble(2) / 1000),
                args.toArray());
    }
}
