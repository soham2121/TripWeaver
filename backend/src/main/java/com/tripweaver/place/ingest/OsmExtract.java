package com.tripweaver.place.ingest;

import java.util.List;
import java.util.Map;

/**
 * Filtered, compact OSM data for one destination. Derived from OpenStreetMap (© OpenStreetMap
 * contributors, ODbL).
 *
 * @param sourceFile extract file name (e.g. Geofabrik western-zone-261122.osm.pbf)
 * @param sourceTimestamp OSM replication timestamp of the extract (ISO-8601), or null
 * @param boundaryOuters destination boundary outer rings, each a list of {lat, lon}
 * @param boundaryInners boundary holes
 * @param features named POI candidates (nodes, way centroids, relation centroids)
 * @param settlements named place=city/town/village/suburb/neighbourhood/hamlet nodes
 */
public record OsmExtract(
        String sourceFile,
        String sourceTimestamp,
        String boundaryName,
        List<double[][]> boundaryOuters,
        List<double[][]> boundaryInners,
        List<Feature> features,
        List<Feature> settlements) {

    /** {@code type} is "node", "way" or "relation". */
    public record Feature(String type, long id, double lat, double lon, Map<String, String> tags) {
        public String osmRef() {
            return type + "/" + id;
        }
    }

    public Polygon boundary() {
        return new Polygon(boundaryOuters, boundaryInners);
    }
}
