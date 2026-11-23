package com.tripweaver.routing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransportModeTest {

    @Test
    void shortLegsUseLocalSpeedLongLegsHighwaySpeed() {
        assertEquals(0, TransportMode.CAR.minutes(0.01));
        assertEquals(22 + 5, TransportMode.CAR.minutes(10)); // 10 km at 28 km/h + 5 min overhead
        // 10 km at 28 km/h (21.4 min) + 120 km at 60 km/h (120 min) → 142 + 5
        assertEquals(142 + 5, TransportMode.CAR.minutes(130));
        assertEquals(600 / 4.5 * 60, TransportMode.WALK.minutes(600), 1); // walking has no highway speed
        assertTrue(TransportMode.CAR.basis().contains("60 km/h"));
    }

    @Test
    void mumbaiToGoaEstimateIsAPlausibleDrive() {
        double km = TransportMode.CAR.roadKm(18.94, 72.83, 15.52, 73.77);
        int h = TransportMode.CAR.minutes(km) / 60;
        assertTrue(h >= 8 && h <= 11, h + " h");
    }
}
