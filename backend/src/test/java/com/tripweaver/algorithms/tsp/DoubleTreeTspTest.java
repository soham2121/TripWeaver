package com.tripweaver.algorithms.tsp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DoubleTreeTspTest {

    static double[][] euclid(double[][] pts) {
        int n = pts.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) d[i][j] = Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]);
        return d;
    }

    static double[][] randomEuclid(Random r, int n) {
        double[][] pts = new double[n][2];
        for (double[] p : pts) {
            p[0] = r.nextDouble() * 100;
            p[1] = r.nextDouble() * 100;
        }
        return euclid(pts);
    }

    static void assertValidClosedTour(int[] tour, int n, int base) {
        assertEquals(n + 1, tour.length);
        assertEquals(base, tour[0]);
        assertEquals(base, tour[n]);
        int[] seen = new int[n];
        for (int i = 0; i < n; i++) seen[tour[i]]++;
        for (int s : seen) assertEquals(1, s, "every stop exactly once: " + Arrays.toString(tour));
    }

    @Test
    void oneVertex() {
        TourResult r = DoubleTreeTsp.closedTour(new double[][] {{0}}, 0);
        assertArrayEquals(new int[] {0, 0}, r.tour());
        assertEquals(0, r.cost());
        assertTrue(r.approximationGuaranteeApplies());
    }

    @Test
    void twoVertices() {
        TourResult r = DoubleTreeTsp.closedTour(new double[][] {{0, 5}, {5, 0}}, 1);
        assertArrayEquals(new int[] {1, 0, 1}, r.tour());
        assertEquals(10, r.cost(), 1e-12);
    }

    @Test
    void smallKnownMetricSquare() {
        double[][] d = euclid(new double[][] {{0, 0}, {0, 1}, {1, 1}, {1, 0}});
        TourResult r = DoubleTreeTsp.closedTour(d, 0);
        assertValidClosedTour(r.tour(), 4, 0);
        assertEquals(4.0, r.cost(), 1e-9);
        assertEquals(3.0, r.mstCost(), 1e-9);
    }

    @Test
    void asymmetricMatrixRejected() {
        double[][] d = {{0, 1, 2}, {1, 0, 1}, {2, 3, 0}};
        MetricReport m = MetricValidator.validate(d);
        assertFalse(m.symmetric());
        assertFalse(m.isMetric());
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(d, 0));
    }

    @Test
    void triangleViolationRunsWithoutGuarantee() {
        double[][] d = {{0, 1, 10}, {1, 0, 1}, {10, 1, 0}};
        MetricReport m = MetricValidator.validate(d);
        assertTrue(m.symmetric());
        assertFalse(m.triangleInequality());
        assertFalse(m.violations().isEmpty());
        TourResult r = DoubleTreeTsp.closedTour(d, 0);
        assertValidClosedTour(r.tour(), 3, 0);
        assertFalse(r.approximationGuaranteeApplies());
    }

    @Test
    void nonFiniteNegativeOrNonSquareRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DoubleTreeTsp.closedTour(new double[][] {{0, Double.POSITIVE_INFINITY}, {1, 0}}, 0));
        assertThrows(
                IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(new double[][] {{0, -1}, {-1, 0}}, 0));
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(new double[][] {{0, 1}}, 0));
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(new double[][] {{1}}, 0));
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(new double[][] {{0}}, 1));
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.closedTour(new double[0][0], 0));
    }

    @Test
    void duplicateCoordinatesWithZeroDistanceAllowed() {
        double[][] d = euclid(new double[][] {{0, 0}, {3, 4}, {3, 4}, {0, 4}});
        assertTrue(MetricValidator.validate(d).isMetric());
        TourResult r = DoubleTreeTsp.closedTour(d, 0);
        assertValidClosedTour(r.tour(), 4, 0);
        assertTrue(r.approximationGuaranteeApplies());
    }

    @Test
    void everyStopVisitedOnceAndTourReturnsToNonZeroBase() {
        Random rnd = new Random(8);
        for (int t = 0; t < 100; t++) {
            int n = 1 + rnd.nextInt(40);
            int base = rnd.nextInt(n);
            TourResult r = DoubleTreeTsp.closedTour(randomEuclid(rnd, n), base);
            assertValidClosedTour(r.tour(), n, base);
        }
    }

    @Test
    void openPathKeepsFixedEndpointsAndClaimsNoBound() {
        Random rnd = new Random(4);
        double[][] d = randomEuclid(rnd, 8);
        TourResult r = DoubleTreeTsp.openPath(d, 2, 5);
        assertEquals(8, r.tour().length);
        assertEquals(2, r.tour()[0]);
        assertEquals(5, r.tour()[7]);
        assertEquals(8, Arrays.stream(r.tour()).distinct().count());
        assertFalse(r.approximationGuaranteeApplies());
        assertThrows(IllegalArgumentException.class, () -> DoubleTreeTsp.openPath(d, 3, 3));
    }

    @Test
    void withinTwiceExactOptimumOnMetricInstances() {
        Random rnd = new Random(2026);
        for (int t = 0; t < 150; t++) {
            int n = 2 + rnd.nextInt(9);
            double[][] d = randomEuclid(rnd, n);
            TourResult approx = DoubleTreeTsp.closedTour(d, 0);
            TourResult exact = ExactTsp.closedTour(d, 0);
            assertValidClosedTour(exact.tour(), n, 0);
            assertTrue(approx.cost() >= exact.cost() - 1e-9);
            assertTrue(approx.cost() <= 2 * exact.cost() + 1e-9, "2-approx violated");
            assertTrue(approx.mstCost() <= exact.cost() + 1e-9, "MST is a lower bound on OPT");
            assertTrue(approx.cost() <= 2 * approx.mstCost() + 1e-9);
        }
    }

    @Test
    void heldKarpMatchesPermutationBruteForce() {
        Random rnd = new Random(99);
        for (int t = 0; t < 40; t++) {
            int n = 1 + rnd.nextInt(7);
            double[][] d = randomEuclid(rnd, n);
            assertEquals(bruteForce(d), ExactTsp.closedTour(d, 0).cost(), 1e-9);
        }
        assertThrows(IllegalArgumentException.class, () -> ExactTsp.closedTour(randomEuclid(rnd, 17), 0));
    }

    @Test
    void nearestNeighbourBaselineIsValidTour() {
        Random rnd = new Random(1);
        double[][] d = randomEuclid(rnd, 25);
        TourResult nn = NearestNeighbourTsp.closedTour(d, 3);
        assertValidClosedTour(nn.tour(), 25, 3);
        assertFalse(nn.approximationGuaranteeApplies());
        assertEquals(TourResult.cost(d, nn.tour()), nn.cost(), 1e-9);
    }

    static double bruteForce(double[][] d) {
        int n = d.length;
        if (n == 1) return 0;
        int[] perm = new int[n - 1];
        for (int i = 0; i < n - 1; i++) perm[i] = i + 1;
        double[] best = {Double.POSITIVE_INFINITY};
        permute(perm, 0, d, best);
        return best[0];
    }

    static void permute(int[] p, int k, double[][] d, double[] best) {
        if (k == p.length) {
            double c = d[0][p[0]] + d[p[p.length - 1]][0];
            for (int i = 0; i + 1 < p.length; i++) c += d[p[i]][p[i + 1]];
            best[0] = Math.min(best[0], c);
            return;
        }
        for (int i = k; i < p.length; i++) {
            int tmp = p[k];
            p[k] = p[i];
            p[i] = tmp;
            permute(p, k + 1, d, best);
            p[i] = p[k];
            p[k] = tmp;
        }
    }
}
