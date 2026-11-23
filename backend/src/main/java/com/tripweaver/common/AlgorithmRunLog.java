package com.tripweaver.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Persists compact algorithm-run metadata (name, version, input size, seed, duration, output summary). */
@Component
public class AlgorithmRunLog {

    public static final String VERSION = "1.0";

    public record Run(
            UUID id,
            OffsetDateTime createdAt,
            String context,
            UUID tripId,
            String algorithm,
            String version,
            int inputSize,
            Long seed,
            Long durationMicros,
            String summaryJson) {}

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AlgorithmRunLog(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID record(
            String context,
            UUID tripId,
            String algorithm,
            int inputSize,
            Long seed,
            Long durationMicros,
            Map<String, ?> summary) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO algorithm_runs (id, created_at, context, trip_id, algorithm, algorithm_version, input_size, seed, duration_micros, summary_json) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id,
                OffsetDateTime.now(ZoneOffset.UTC),
                context,
                tripId,
                algorithm,
                VERSION,
                inputSize,
                seed,
                durationMicros,
                json.writeValueAsString(summary));
        return id;
    }

    public List<Run> recent(int limit) {
        return jdbc.query(
                "SELECT id, created_at, context, trip_id, algorithm, algorithm_version, input_size, seed, duration_micros, summary_json FROM algorithm_runs ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new Run(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, OffsetDateTime.class),
                        rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getInt(7),
                        (Long) rs.getObject(8),
                        (Long) rs.getObject(9),
                        rs.getString(10)),
                limit);
    }
}
