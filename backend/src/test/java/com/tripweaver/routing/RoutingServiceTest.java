package com.tripweaver.routing;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** OSRM parsing against a local stub server, and fallback when the provider fails. */
class RoutingServiceTest {

    static final List<LatLon> PTS =
            List.of(new LatLon(15.52, 73.766), new LatLon(15.50, 73.77), new LatLon(15.49, 73.81));

    HttpServer server;
    final List<String> requests = new ArrayList<>();

    String start(String tableJson, String routeJson) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            requests.add(path);
            byte[] body = (path.startsWith("/table") ? tableJson : routeJson).getBytes(StandardCharsets.UTF_8);
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
}
