package com.tripweaver.place.ingest;

import java.util.List;

/**
 * Multipolygon of closed rings, each ring an array of {lat, lon}. A point is inside when it lies in an
 * odd number of rings (even-odd rule), which handles islands (several outers) and holes (inners).
 * Planar ray casting on lat/lon is adequate at state scale.
 */
public record Polygon(List<double[][]> outers, List<double[][]> inners) {

    public boolean contains(double lat, double lon) {
        int count = 0;
        for (double[][] r : outers) if (inRing(r, lat, lon)) count++;
        for (double[][] r : inners) if (inRing(r, lat, lon)) count++;
        return count % 2 == 1;
    }

    private static boolean inRing(double[][] ring, double lat, double lon) {
        boolean in = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double yi = ring[i][0], xi = ring[i][1], yj = ring[j][0], xj = ring[j][1];
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
}
