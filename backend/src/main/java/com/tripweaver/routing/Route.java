package com.tripweaver.routing;

import java.util.List;

/**
 * Road route through ordered waypoints for drawing and navigation. Leg minutes are driving time only (no per-leg
 * overhead). {@code geometry} is a list of [lon, lat] pairs (GeoJSON order).
 */
public record Route(
        String provider,
        String basis,
        List<double[]> geometry,
        List<Leg> legs,
        int totalMinutes,
        double totalKm,
        List<String> warnings) {

    public record Leg(int minutes, double km) {}
}
