package com.tripweaver.algorithms.tsp;

import java.util.List;

/** Result of checking a distance matrix against the metric assumptions of Double-Tree TSP. */
public record MetricReport(
        boolean square,
        boolean finite,
        boolean nonNegative,
        boolean zeroDiagonal,
        boolean symmetric,
        boolean triangleInequality,
        List<String> violations) {

    /** All conditions for the 2-approximation guarantee hold. */
    public boolean isMetric() {
        return usableForTree() && triangleInequality;
    }

    /** Conditions needed to build an undirected MST at all. */
    public boolean usableForTree() {
        return square && finite && nonNegative && zeroDiagonal && symmetric;
    }
}
