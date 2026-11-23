package com.tripweaver.routing;

import com.tripweaver.common.Geo;

/**
 * Travel estimate used when road routing is unavailable: straight-line distance × {@link #ROAD_FACTOR}, the first
 * {@link #LOCAL_KM} km at a local speed and the rest at a highway speed, plus a fixed per-leg overhead (parking,
 * finding the entrance). Always labelled as an estimate.
 */
public enum TransportMode {
    CAR(28, 60, 5, 5),
    SCOOTER(25, 45, 3, 2),
    TAXI(28, 60, 5, 0),
    WALK(4.5, 4.5, 0, 0);

    public static final double ROAD_FACTOR = 1.3;
    /** Road km driven at the local speed before the highway speed applies. */
    public static final double LOCAL_KM = 10;

    private final double kmh;
    private final double highwayKmh;
    private final int legOverheadMinutes;
    private final int seatsPerVehicle;

    TransportMode(double kmh, double highwayKmh, int legOverheadMinutes, int seatsPerVehicle) {
        this.kmh = kmh;
        this.highwayKmh = highwayKmh;
        this.legOverheadMinutes = legOverheadMinutes;
        this.seatsPerVehicle = seatsPerVehicle;
    }

    public double roadKm(double lat1, double lon1, double lat2, double lon2) {
        return Geo.haversineKm(lat1, lon1, lat2, lon2) * ROAD_FACTOR;
    }

    public int legOverheadMinutes() {
        return legOverheadMinutes;
    }

    public int minutes(double roadKm) {
        if (roadKm < 0.05) return 0;
        double local = Math.min(roadKm, LOCAL_KM), highway = roadKm - local;
        return (int) Math.ceil(local / kmh * 60 + highway / highwayKmh * 60) + legOverheadMinutes;
    }

    /** Vehicles whose fuel the party pays for (0 for taxi and walking). */
    public int vehicles(int party) {
        return seatsPerVehicle == 0 ? 0 : (party + seatsPerVehicle - 1) / seatsPerVehicle;
    }

    public String basis() {
        return this == WALK
                ? "Estimate: straight-line distance × 1.3 at 4.5 km/h (no road routing)"
                : "Estimate: straight-line distance × 1.3 at " + (int) kmh + " km/h for the first " + (int) LOCAL_KM
                        + " km and " + (int) highwayKmh + " km/h beyond, + " + legOverheadMinutes
                        + " min per leg (no road routing)";
    }
}
