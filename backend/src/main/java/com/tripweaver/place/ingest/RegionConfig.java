package com.tripweaver.place.ingest;

import java.util.List;

/** Destination and curated travel regions (data/curated/&lt;destination&gt;-regions.json). */
public record RegionConfig(Dest destination, String note, List<RegionDef> regions) {

    public record Dest(String slug, String name, String osmBoundaryName, String osmAdminLevel) {}

    /** A region is anchored at a named OSM settlement; places join the nearest anchor. */
    public record RegionDef(String slug, String name, String anchor) {}
}
