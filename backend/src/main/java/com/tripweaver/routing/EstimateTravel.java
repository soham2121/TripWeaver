package com.tripweaver.routing;

import java.util.ArrayList;
import java.util.List;

/** Straight-line estimate ({@link TransportMode#basis()}); always available, used offline and as fallback. */
public final class EstimateTravel implements TravelProvider {

    public static final EstimateTravel INSTANCE = new EstimateTravel();

    @Override
    public TravelMatrix matrix(List<LatLon> points, TransportMode mode) {
        return matrix(points, mode, List.of());
    }

    TravelMatrix matrix(List<LatLon> points, TransportMode mode, List<String> warnings) {
        int n = points.size();
        int[][] min = new int[n][n];
        double[][] km = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                km[i][j] = leg(points.get(i), points.get(j), mode);
                min[i][j] = mode.minutes(km[i][j]);
            }
        return new TravelMatrix(min, km, "ESTIMATE", mode.basis(), 0, List.copyOf(warnings));
    }

    static double leg(LatLon a, LatLon b, TransportMode mode) {
        return mode.roadKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    @Override
    public Route route(List<LatLon> waypoints, TransportMode mode) {
        return route(waypoints, mode, List.of());
    }

    Route route(List<LatLon> waypoints, TransportMode mode, List<String> warnings) {
        List<double[]> geometry = new ArrayList<>();
        List<Route.Leg> legs = new ArrayList<>();
        int total = 0;
        double totalKm = 0;
        for (int i = 0; i < waypoints.size(); i++) {
            geometry.add(new double[] {waypoints.get(i).lon(), waypoints.get(i).lat()});
            if (i == 0) continue;
            double km = leg(waypoints.get(i - 1), waypoints.get(i), mode);
            int m = km < 0.05 ? 0 : mode.minutes(km) - mode.legOverheadMinutes();
            legs.add(new Route.Leg(m, km));
            total += m;
            totalKm += km;
        }
        return new Route(
                "ESTIMATE",
                mode.basis() + "; drawn as straight lines",
                geometry,
                legs,
                total,
                totalKm,
                List.copyOf(warnings));
    }
}
