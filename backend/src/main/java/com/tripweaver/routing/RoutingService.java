package com.tripweaver.routing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Self-hosted OSRM (car profile) for travel tables and route geometry. Falls back to {@link EstimateTravel} when
 * OSRM is not configured, when walking (no foot profile is built), or when a request fails. The fallback is
 * labelled and warned about, so a provider outage never loses a plan. Directed pairs that OSRM cannot route are
 * filled with the estimate and counted.
 */
@Service
public class RoutingService implements TravelProvider {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    /** Route provider value when OSRM answers that no road connects the waypoints (not an outage). */
    public static final String NO_ROUTE = "NO_ROUTE";

    static final class NoRouteException extends RuntimeException {}

    public static final String BASIS = "OSRM road network (OpenStreetMap, car profile)";

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public RoutingService(
            @Value("${tripweaver.routing.osrm-url:}") String baseUrl,
            @Value("${tripweaver.routing.timeout-ms:5000}") int timeoutMs) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // osrm-routed speaks HTTP/1.1; skip the h2c upgrade
                .connectTimeout(timeout)
                .build();
    }

    boolean usesOsrm(TransportMode mode) {
        return !baseUrl.isBlank() && mode != TransportMode.WALK;
    }

    @Override
    public TravelMatrix matrix(List<LatLon> points, TransportMode mode) {
        if (!usesOsrm(mode) || points.size() < 2) return EstimateTravel.INSTANCE.matrix(points, mode);
        try {
            int n = points.size();
            JsonNode[][] dur = new JsonNode[n][n], dist = new JsonNode[n][n];
            table(points, dur, dist);
            int[][] min = new int[n][n];
            double[][] km = new double[n][n];
            int estimated = 0;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    JsonNode s = dur[i][j];
                    JsonNode m = dist[i][j];
                    if (s == null || s.isNull() || m == null || m.isNull()) {
                        km[i][j] = EstimateTravel.leg(points.get(i), points.get(j), mode);
                        min[i][j] = mode.minutes(km[i][j]);
                        estimated++;
                        continue;
                    }
                    km[i][j] = m.asDouble() / 1000;
                    min[i][j] = km[i][j] < 0.05 ? 0 : (int) Math.ceil(s.asDouble() / 60) + mode.legOverheadMinutes();
                }
            List<String> w = estimated == 0
                    ? List.of()
                    : List.of(estimated + " leg(s) had no road route and use the straight-line estimate.");
            return new TravelMatrix(
                    min, km, "OSRM", BASIS + " + " + mode.legOverheadMinutes() + " min per leg", estimated, w);
        } catch (Exception e) {
            log.warn("OSRM table failed: {}", e.toString());
            return EstimateTravel.INSTANCE.matrix(points, mode, List.of(UNAVAILABLE));
        }
    }

    /** OSRM's default --max-table-size; the public demo server enforces it (larger requests get TooBig). */
    static final int MAX_TABLE_COORDS = 100;

    /**
     * Fills the directed duration and distance tables. Up to {@link #MAX_TABLE_COORDS} points take one request;
     * more are split into blocks of half that size, one request per (source block, destination block) pair.
     */
    private void table(List<LatLon> points, JsonNode[][] dur, JsonNode[][] dist) throws Exception {
        int n = points.size();
        int b = n <= MAX_TABLE_COORDS ? n : MAX_TABLE_COORDS / 2;
        for (int r = 0; r < n; r += b)
            for (int c = 0; c < n; c += b) {
                List<LatLon> rows = points.subList(r, Math.min(n, r + b));
                List<LatLon> cols = points.subList(c, Math.min(n, c + b));
                String q = "?annotations=duration,distance";
                List<LatLon> pts = rows;
                if (r != c) {
                    pts = new ArrayList<>(rows);
                    pts.addAll(cols);
                    q += "&sources=" + range(0, rows.size()) + "&destinations=" + range(rows.size(), pts.size());
                }
                JsonNode t = get("/table/v1/driving/" + coords(pts) + q);
                for (int i = 0; i < rows.size(); i++)
                    for (int j = 0; j < cols.size(); j++) {
                        dur[r + i][c + j] = t.get("durations").get(i).get(j);
                        dist[r + i][c + j] = t.get("distances").get(i).get(j);
                    }
            }
    }

    private static String range(int from, int to) {
        StringJoiner j = new StringJoiner(";");
        for (int i = from; i < to; i++) j.add(Integer.toString(i));
        return j.toString();
    }

    @Override
    public Route route(List<LatLon> waypoints, TransportMode mode) {
        if (!usesOsrm(mode) || waypoints.size() < 2) return EstimateTravel.INSTANCE.route(waypoints, mode);
        try {
            JsonNode r = get("/route/v1/driving/" + coords(waypoints) + "?overview=full&geometries=geojson&steps=false")
                    .get("routes")
                    .get(0);
            List<double[]> geometry = new ArrayList<>();
            for (JsonNode c : r.get("geometry").get("coordinates"))
                geometry.add(new double[] {c.get(0).asDouble(), c.get(1).asDouble()});
            List<Route.Leg> legs = new ArrayList<>();
            int total = 0;
            for (JsonNode l : r.get("legs")) {
                int m = (int) Math.ceil(l.get("duration").asDouble() / 60);
                legs.add(new Route.Leg(m, l.get("distance").asDouble() / 1000));
                total += m;
            }
            return new Route(
                    "OSRM", BASIS, geometry, legs, total, r.get("distance").asDouble() / 1000, List.of());
        } catch (NoRouteException e) {
            return new Route(
                    NO_ROUTE, "OSRM found no road between the waypoints", List.of(), List.of(), 0, 0, List.of());
        } catch (Exception e) {
            log.warn("OSRM route failed: {}", e.toString());
            return EstimateTravel.INSTANCE.route(waypoints, mode, List.of(UNAVAILABLE));
        }
    }

    static final String UNAVAILABLE = "Road routing (OSRM) is unavailable; travel times are straight-line estimates.";

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode body = json.readTree(res.body());
        if ("NoRoute".equals(body.path("code").asString())) throw new NoRouteException();
        if (res.statusCode() != 200 || !"Ok".equals(body.path("code").asString()))
            throw new IllegalStateException(
                    "OSRM " + res.statusCode() + " " + body.path("code").asString());
        return body;
    }

    private static String coords(List<LatLon> pts) {
        StringJoiner j = new StringJoiner(";");
        for (LatLon p : pts) j.add(String.format(Locale.ROOT, "%.6f,%.6f", p.lon(), p.lat()));
        return j.toString();
    }
}
