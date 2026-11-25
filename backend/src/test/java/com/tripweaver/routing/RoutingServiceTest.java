package com.tripweaver.routing;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** OSRM parsing against a local stub server, and fallback when the provider fails. */
class RoutingServiceTest {

    static final List<LatLon> PTS =
            List.of(new LatLon(15.52, 73.766), new LatLon(15.50, 73.77), new LatLon(15.49, 73.81));

    HttpServer server;
    final List<String> requests = new ArrayList<>();

    String start(String tableJson, String routeJson) throws IOException {
        return start(path -> path.startsWith("/table") ? tableJson : routeJson);
    }

    String start(UnaryOperator<String> respond) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            requests.add(path);
            byte[] body = respond.apply(path).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    static final String TABLE = """
            {"code":"Ok",
             "durations":[[0,300,600],[310,0,null],[590,420,0]],
             "distances":[[0,2500,6000],[2600,0,null],[5900,4100,0]]}""";
    static final String ROUTE = """
            {"code":"Ok","routes":[{"distance":8600,"duration":900,
              "geometry":{"type":"LineString","coordinates":[[73.766,15.52],[73.768,15.51],[73.77,15.50],[73.81,15.49]]},
              "legs":[{"distance":2500,"duration":300},{"distance":6100,"duration":600}]}]}""";

    @Test
    void osrmTableGivesDirectedMinutesAndFillsUnroutablePairsWithEstimates() throws IOException {
        RoutingService rs = new RoutingService(start(TABLE, ROUTE), 2000);
        TravelMatrix m = rs.matrix(PTS, TransportMode.CAR);
        assertEquals("OSRM", m.provider());
        assertEquals(5 + 5, m.minutes()[0][1]); // 300 s + 5 min car leg overhead
        assertEquals(6 + 5, m.minutes()[1][0]); // directed: 310 s rounds up
        assertEquals(2.5, m.km()[0][1], 1e-9);
        assertEquals(0, m.minutes()[1][1]);
        // null pair → straight-line estimate, flagged
        assertTrue(m.minutes()[1][2] > 0);
        assertEquals(1, m.estimatedPairs());
        assertTrue(m.warnings().getFirst().contains("1 leg"));
        assertTrue(requests.getFirst().startsWith("/table/v1/driving/73.766000,15.520000;"));
        assertTrue(requests.getFirst().contains("annotations=duration,distance"));
    }

    @Test
    void osrmRouteGivesGeometryAndLegs() throws IOException {
        RoutingService rs = new RoutingService(start(TABLE, ROUTE), 2000);
        Route r = rs.route(PTS, TransportMode.CAR);
        assertEquals("OSRM", r.provider());
        assertEquals(4, r.geometry().size());
        assertArrayEquals(new double[] {73.766, 15.52}, r.geometry().getFirst());
        assertEquals(2, r.legs().size());
        assertEquals(10, r.legs().get(1).minutes());
        assertEquals(6.1, r.legs().get(1).km(), 1e-9);
        assertEquals(8.6, r.totalKm(), 1e-9);
    }

    /**
     * Stub of a 100-coordinate OSRM table: point k sits at lon 73 + k/1000. Travel from point a to point b takes
     * (b + 1) minutes and covers (a + 1) km, so every cell shows which pair the blocks put there.
     */
    static String table(String path) {
        String[] parts = path.substring("/table/v1/driving/".length()).split("[?]", 2);
        int[] pts = Arrays.stream(parts[0].split(";"))
                .mapToInt(c -> (int) Math.round((Double.parseDouble(c.split(",")[0]) - 73) * 1000))
                .toArray();
        if (pts.length > RoutingService.MAX_TABLE_COORDS) return "{\"code\":\"TooBig\"}";
        int[] src = indices(parts[1], "sources", pts.length), dst = indices(parts[1], "destinations", pts.length);
        StringBuilder d = new StringBuilder(), m = new StringBuilder();
        for (int i : src) {
            StringJoiner dr = new StringJoiner(",", "[", "]"), mr = new StringJoiner(",", "[", "]");
            for (int j : dst) {
                dr.add(Integer.toString(60 * (pts[j] + 1)));
                mr.add(Integer.toString(1000 * (pts[i] + 1)));
            }
            d.append(d.isEmpty() ? "" : ",").append(dr);
            m.append(m.isEmpty() ? "" : ",").append(mr);
        }
        return "{\"code\":\"Ok\",\"durations\":[" + d + "],\"distances\":[" + m + "]}";
    }

    static int[] indices(String query, String key, int n) {
        for (String kv : query.split("&"))
            if (kv.startsWith(key + "="))
                return Arrays.stream(kv.substring(key.length() + 1).split(";"))
                        .mapToInt(Integer::parseInt)
                        .toArray();
        return java.util.stream.IntStream.range(0, n).toArray();
    }

    @Test
    void tablesOverTheCoordinateLimitAreSplitIntoBlocks() throws IOException {
        RoutingService rs = new RoutingService(start(RoutingServiceTest::table), 2000);
        List<LatLon> pts = new ArrayList<>();
        for (int k = 0; k < 121; k++) pts.add(new LatLon(15.5, 73 + k / 1000.0)); // 3-day pool + base
        TravelMatrix m = rs.matrix(pts, TransportMode.CAR);
        assertEquals("OSRM", m.provider());
        assertEquals(0, m.estimatedPairs());
        assertEquals(9, requests.size()); // blocks of 50: 3 x 3
        for (int i = 0; i < 121; i++)
            for (int j = 0; j < 121; j++)
                if (i != j) {
                    assertEquals(j + 1 + 5, m.minutes()[i][j], i + "->" + j);
                    assertEquals(i + 1, m.km()[i][j], 1e-9, i + "->" + j);
                }
        requests.clear();
        rs.matrix(pts.subList(0, 100), TransportMode.CAR);
        assertEquals(1, requests.size(), "100 points still fit one request");
    }

    @Test
    void providerFailureFallsBackToLabelledEstimates() {
        RoutingService down = new RoutingService("http://127.0.0.1:9", 500); // nothing listens on port 9
        TravelMatrix m = down.matrix(PTS, TransportMode.CAR);
        assertEquals("ESTIMATE", m.provider());
        assertTrue(m.warnings().getFirst().contains("unavailable"));
        Route r = down.route(PTS, TransportMode.CAR);
        assertEquals("ESTIMATE", r.provider());
        assertEquals(3, r.geometry().size()); // straight segments through the waypoints
        assertFalse(r.warnings().isEmpty());
    }

    @Test
    void notConfiguredOrWalkingUsesEstimateWithoutWarnings() throws IOException {
        TravelMatrix off = new RoutingService("", 500).matrix(PTS, TransportMode.CAR);
        assertEquals("ESTIMATE", off.provider());
        assertTrue(off.warnings().isEmpty());
        TravelMatrix walk = new RoutingService(start(TABLE, ROUTE), 2000).matrix(PTS, TransportMode.WALK);
        assertEquals("ESTIMATE", walk.provider());
        assertTrue(requests.isEmpty(), "car-profile OSRM must not be used for walking");
    }

    @Test
    void osrmErrorCodeIsAFailure() throws IOException {
        RoutingService rs = new RoutingService(start("{\"code\":\"InvalidQuery\"}", ROUTE), 2000);
        assertEquals("ESTIMATE", rs.matrix(PTS, TransportMode.CAR).provider());
    }

    @Test
    void osrmNoRouteIsReportedAsUnreachableNotAsAnOutage() throws IOException {
        RoutingService rs =
                new RoutingService(start(TABLE, "{\"code\":\"NoRoute\",\"message\":\"Impossible route\"}"), 2000);
        Route r = rs.route(PTS, TransportMode.CAR);
        assertEquals("NO_ROUTE", r.provider());
        assertTrue(r.legs().isEmpty());
    }
}
