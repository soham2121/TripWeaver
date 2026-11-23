package com.tripweaver.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Google Maps directions URLs (Maps URLs API, {@code api=1}): no key, nothing scraped. Each URL carries at most
 * {@value #MAX_WAYPOINTS} intermediate waypoints (the documented desktop limit; mobile browsers may show fewer),
 * so long days are split into chained segments where each segment starts at the previous destination.
 */
public final class NavigationLinks {

    public static final int MAX_WAYPOINTS = 9;

    private NavigationLinks() {}

    public static List<String> google(List<LatLon> points, TransportMode mode) {
        List<String> urls = new ArrayList<>();
        String travel = mode == TransportMode.WALK ? "walking" : "driving";
        for (int s = 0; s + 1 < points.size(); ) {
            int dest = Math.min(s + MAX_WAYPOINTS + 1, points.size() - 1);
            StringBuilder u = new StringBuilder("https://www.google.com/maps/dir/?api=1")
                    .append("&origin=")
                    .append(fmt(points.get(s)))
                    .append("&destination=")
                    .append(fmt(points.get(dest)));
            if (dest - s > 1) {
                List<String> w = new ArrayList<>();
                for (int i = s + 1; i < dest; i++) w.add(fmt(points.get(i)));
                u.append("&waypoints=").append(String.join("%7C", w));
            }
            urls.add(u.append("&travelmode=").append(travel).toString());
            s = dest;
        }
        return urls;
    }

    private static String fmt(LatLon p) {
        return String.format(Locale.ROOT, "%.6f,%.6f", p.lat(), p.lon());
    }
}
