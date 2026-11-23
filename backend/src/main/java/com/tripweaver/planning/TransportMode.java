package com.tripweaver.planning;

import com.tripweaver.common.Geo;

/**
 * Local travel estimate used until road routing exists (Phase 7): straight-line distance × {@link #ROAD_FACTOR} at
 * an assumed average speed, plus a fixed per-leg overhead (parking, finding the entrance). Always labelled as an
 * estimate.
 */
public enum TransportMode {
    CAR(28, 5, 5),
    SCOOTER(25, 3, 2),
    TAXI(28, 5, 0),
    WALK(4.5, 0, 0);

    public static final double ROAD_FACTOR = 1.3;

    private final double kmh;
    private final int legOverheadMinutes;
    private final int seatsPerVehicle;

    TransportMode(double kmh, int legOverheadMinutes, int seatsPerVehicle) {
        this.kmh = kmh;
        this.legOverheadMinutes = legOverheadMinutes;
        this.seatsPerVehicle = seatsPerVehicle;
    }

    public double roadKm(double lat1, double lon1, double lat2, double lon2) {
        return Geo.haversineKm(lat1, lon1, lat2, lon2) * ROAD_FACTOR;
    }

    public int minutes(double roadKm) {
        if (roadKm < 0.05) return 0;
        return (int) Math.ceil(roadKm / kmh * 60) + legOverheadMinutes;
    }

    /** Vehicles whose fuel the party pays for (0 for taxi and walking). */
    public int vehicles(int party) {
        return seatsPerVehicle == 0 ? 0 : (party + seatsPerVehicle - 1) / seatsPerVehicle;
    }

    public String basis() {
        return this == WALK
                ? "Estimate: straight-line distance × 1.3 at 4.5 km/h (no road routing yet)"
                : "Estimate: straight-line distance × 1.3 at " + (int) kmh + " km/h + " + legOverheadMinutes
                        + " min per leg (no road routing yet)";
    }
}
