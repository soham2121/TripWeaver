package com.tripweaver.place.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorridorBoundaryTest {

    static double[][] square(double lat0, double lon0, double lat1, double lon1) {
        return new double[][] {{lat0, lon0}, {lat0, lon1}, {lat1, lon1}, {lat1, lon0}, {lat0, lon0}};
    }

    @Test
    void bboxMinusExcludedStateKeepsOnlyTheCorridor() {
        // "Goa": a mainland square plus an island, with a hole (enclave that is not Goa).
        OsmExtract goa = new OsmExtract(
                "x.pbf",
                "t",
                "Goa",
                List.<double[][]>of(square(15.0, 73.7, 15.8, 74.3), square(15.85, 73.70, 15.87, 73.72)),
                List.<double[][]>of(square(15.4, 74.0, 15.5, 74.1)),
                List.of(),
                List.of());
        OsmExtract corridor =
                OsmExtractTool.bboxMinus(goa, new OsmPbfExtractor.Bbox(15.6, 72.75, 19.1, 74.1), "Konkan corridor");
        Polygon p = corridor.boundary();
        assertEquals("Konkan corridor", corridor.boundaryName());
        assertTrue(p.contains(17.0, 73.3), "Ratnagiri is in the corridor");
        assertFalse(p.contains(15.7, 73.9), "inside Goa");
        assertFalse(p.contains(15.86, 73.71), "Goa island");
        assertFalse(p.contains(19.5, 73.0), "north of the bbox");
        assertFalse(p.contains(17.0, 74.5), "east of the bbox");
        assertFalse(p.contains(15.3, 74.0), "Goa south of the bbox");
        assertFalse(p.contains(15.45, 74.05), "enclave south of the bbox");
    }
}
