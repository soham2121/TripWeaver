package com.tripweaver.routing;

import java.util.List;

/** Source of travel times and route geometry: self-hosted OSRM or the labelled straight-line estimate. */
public interface TravelProvider {

    TravelMatrix matrix(List<LatLon> points, TransportMode mode);

    Route route(List<LatLon> waypoints, TransportMode mode);
}
