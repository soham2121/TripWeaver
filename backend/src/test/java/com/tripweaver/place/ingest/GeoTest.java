package com.tripweaver.place.ingest;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.common.Geo;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoTest {

    @Test
    void haversineKnownDistance() {
        // Panaji (15.4909, 73.8278) to Margao (15.2832, 73.9862): about 28 km
        double km = Geo.haversineKm(15.4909, 73.8278, 15.2832, 73.9862);
        assertEquals(28.4, km, 1.0);
        assertEquals(0, Geo.haversineKm(15, 73, 15, 73), 1e-12);
    }

    @Test
    void ringAssemblyJoinsWaysInAnyDirection() {
        List<long[]> ways = List.of(new long[] {1, 2, 3}, new long[] {5, 4, 3}, new long[] {5, 6, 1});
        List<long[]> rings = RingAssembler.assemble(ways);
        assertEquals(1, rings.size());
        long[] r = rings.getFirst();
        assertEquals(r[0], r[r.length - 1], "ring is closed");
        assertEquals(7, r.length);
    }

    @Test
    void ringAssemblyKeepsSeparateRingsAndDropsOpenFragments() {
        List<long[]> ways = List.of(
                new long[] {1, 2, 3, 1}, // closed already
                new long[] {10, 11},
                new long[] {11, 12},
                new long[] {12, 10}, // second ring
                new long[] {20, 21} // dangling
                );
        List<long[]> rings = RingAssembler.assemble(ways);
        assertEquals(2, rings.size());
    }

    @Test
    void pointInPolygonWithHole() {
        Polygon p = new Polygon(
                List.<double[][]>of(new double[][] {{0, 0}, {0, 10}, {10, 10}, {10, 0}, {0, 0}}),
                List.<double[][]>of(new double[][] {{4, 4}, {4, 6}, {6, 6}, {6, 4}, {4, 4}}));
        assertTrue(p.contains(1, 1));
        assertFalse(p.contains(5, 5), "inside hole");
        assertFalse(p.contains(11, 5));
        assertTrue(p.contains(9.9, 0.1));
    }

    @Test
    void multipleOuterRingsLikeIslands() {
        Polygon p = new Polygon(
                List.of(
                        new double[][] {{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0, 0}},
                        new double[][] {{5, 5}, {5, 6}, {6, 6}, {6, 5}, {5, 5}}),
                List.of());
        assertTrue(p.contains(5.5, 5.5));
        assertTrue(p.contains(0.5, 0.5));
        assertFalse(p.contains(3, 3));
    }
}
