package com.tripweaver.routing;

import java.util.List;

/**
 * Directed travel between points. {@code minutes[i][j]} includes the per-leg overhead of the transport mode
 * (parking, finding the entrance); {@code km[i][j]} is road distance (or the estimate). Real road times can be
 * asymmetric.
 *
 * @param provider OSRM or ESTIMATE
 * @param estimatedPairs directed pairs filled with the straight-line estimate because the provider had no route
 */
public record TravelMatrix(
        int[][] minutes, double[][] km, String provider, String basis, int estimatedPairs, List<String> warnings) {}
