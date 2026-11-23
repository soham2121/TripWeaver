package com.tripweaver.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NavigationLinksTest {

    static List<LatLon> points(int n) {
        List<LatLon> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new LatLon(15.0 + i * 0.01, 73.8));
        return l;
    }

    static String param(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + name + "=([^&]*)").matcher(url);
        return m.find() ? URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    }

    @Test
    void shortDayIsOneLinkWithAllWaypointsInOrder() {
        List<String> urls = NavigationLinks.google(points(4), TransportMode.CAR);
        assertEquals(1, urls.size());
        String u = urls.getFirst();
        assertTrue(u.startsWith("https://www.google.com/maps/dir/?api=1&"));
        assertEquals("15.000000,73.800000", param(u, "origin"));
        assertEquals("15.030000,73.800000", param(u, "destination"));
        assertEquals("15.010000,73.800000|15.020000,73.800000", param(u, "waypoints"));
        assertEquals("driving", param(u, "travelmode"));
        assertFalse(u.contains("|"), "pipe must be percent-encoded");
    }

    @Test
    void longRoutesAreSplitWithinTheWaypointLimitAndChained() {
        List<LatLon> pts = points(25);
        List<String> urls = NavigationLinks.google(pts, TransportMode.WALK);
        List<String> visited = new ArrayList<>();
        String prevDest = null;
        for (String u : urls) {
            String w = param(u, "waypoints");
            int count = w == null ? 0 : w.split("\\|").length;
            assertTrue(count <= NavigationLinks.MAX_WAYPOINTS);
            if (prevDest != null) assertEquals(prevDest, param(u, "origin"), "segments must chain");
            else visited.add(param(u, "origin"));
            if (w != null) visited.addAll(List.of(w.split("\\|")));
            visited.add(param(u, "destination"));
            prevDest = param(u, "destination");
            assertEquals("walking", param(u, "travelmode"));
        }
        assertEquals(25, visited.size());
        for (int i = 0; i < 25; i++) assertEquals(String.format("%.6f,73.800000", 15.0 + i * 0.01), visited.get(i));
    }

    @Test
    void fewerThanTwoPointsHasNoLink() {
        assertTrue(NavigationLinks.google(points(1), TransportMode.CAR).isEmpty());
    }
}
