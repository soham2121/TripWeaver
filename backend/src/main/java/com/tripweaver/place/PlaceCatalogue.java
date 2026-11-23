package com.tripweaver.place;

import com.tripweaver.common.ApiException;
import com.tripweaver.place.ingest.CatalogueImportedEvent;
import com.tripweaver.search.PlaceSearchIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Holds an immutable search index per destination, built from the active catalogue rows. Rebuilt after
 * every catalogue import.
 */
@Service
public class PlaceCatalogue {

    public static final String DEFAULT_DESTINATION = "goa";

    private final JdbcTemplate jdbc;
    private volatile Map<String, PlaceSearchIndex> indexes;

    public PlaceCatalogue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Index for a destination slug (null = default). 404 if unknown. */
    public PlaceSearchIndex index(String destination) {
        Map<String, PlaceSearchIndex> m = indexes;
        if (m == null) m = reload();
        String slug = destination == null || destination.isBlank() ? DEFAULT_DESTINATION : destination;
        PlaceSearchIndex idx = m.get(slug);
        if (idx == null) throw ApiException.notFound("Destination", slug);
        return idx;
    }

    @EventListener
    public void onImport(CatalogueImportedEvent e) {
        reload();
    }

    public synchronized Map<String, PlaceSearchIndex> reload() {
        Map<UUID, Set<String>> cats = multimap("SELECT place_id, category FROM place_categories");
        Map<UUID, Set<String>> interests = multimap("SELECT place_id, tag FROM place_interest_tags");
        Map<UUID, List<PlaceSummary.CostEstimate>> costs = new HashMap<>();
        jdbc.query(
                "SELECT place_id, kind, amount_min, amount_max, currency, basis, confidence FROM place_cost_estimates",
                rs -> {
                    costs.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new PlaceSummary.CostEstimate(
                                    rs.getString(2),
                                    rs.getDouble(3),
                                    rs.getDouble(4),
                                    rs.getString(5),
                                    rs.getString(6),
                                    rs.getString(7)));
                });
        Set<UUID> withHours =
                new HashSet<>(jdbc.queryForList("SELECT DISTINCT place_id FROM place_opening_hours", UUID.class));

        Map<String, List<PlaceSummary>> byDest = new HashMap<>();
        jdbc.query("""
                SELECT p.id, p.name, p.latitude, p.longitude, p.primary_category, r.slug, r.name, p.neighbourhood,
                       p.is_activity, p.visit_minutes_min, p.visit_minutes_max, p.activity_intensity,
                       p.data_quality_status, p.missing_fields, d.slug
                FROM places p
                JOIN destinations d ON d.id = p.destination_id
                LEFT JOIN regions r ON r.id = p.region_id
                WHERE p.is_active
                """, rs -> {
            UUID id = rs.getObject(1, UUID.class);
            String missing = rs.getString(14);
            byDest.computeIfAbsent(rs.getString(15), k -> new ArrayList<>())
                    .add(new PlaceSummary(
                            id,
                            rs.getString(2),
                            rs.getDouble(3),
                            rs.getDouble(4),
                            rs.getString(5),
                            Set.copyOf(cats.getOrDefault(id, Set.of())),
                            Set.copyOf(interests.getOrDefault(id, Set.of())),
                            rs.getString(6),
                            rs.getString(7),
                            rs.getString(8),
                            rs.getBoolean(9),
                            (Integer) rs.getObject(10),
                            (Integer) rs.getObject(11),
                            rs.getString(12),
                            List.copyOf(costs.getOrDefault(id, List.of())),
                            withHours.contains(id),
                            rs.getString(13),
                            missing == null || missing.isBlank() ? List.of() : Arrays.asList(missing.split(","))));
        });
        Map<String, PlaceSearchIndex> m = new HashMap<>();
        byDest.forEach((slug, ps) -> m.put(slug, PlaceSearchIndex.build(ps)));
        // Destinations with no places yet still resolve.
        for (String slug : jdbc.queryForList("SELECT slug FROM destinations", String.class))
            m.putIfAbsent(slug, PlaceSearchIndex.build(List.of()));
        indexes = Map.copyOf(m);
        return indexes;
    }

    private Map<UUID, Set<String>> multimap(String sql) {
        Map<UUID, Set<String>> m = new HashMap<>();
        jdbc.query(sql, rs -> {
            m.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>()).add(rs.getString(2));
        });
        return m;
    }
}
