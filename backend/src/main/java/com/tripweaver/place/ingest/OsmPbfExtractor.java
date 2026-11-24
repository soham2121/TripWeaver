package com.tripweaver.place.ingest;

import crosby.binary.BinaryParser;
import crosby.binary.Osmformat;
import crosby.binary.file.BlockInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a Geofabrik .osm.pbf and keeps only what TripWeaver needs for one destination: named POI
 * candidates ({@link OsmCategoryMapper}), settlements, and the administrative boundary polygon.
 *
 * <p>Three streaming passes keep memory small on a 200+ MB regional extract: (1) ways + relations —
 * candidate ways, the boundary relation and multipolygon POIs; (2) ways again — node lists of member ways
 * needed by relations; (3) nodes — candidate nodes inside the bbox and coordinates of every referenced
 * node (sorted id array + binary search). Way/relation positions are the mean of their nodes.
 */
public final class OsmPbfExtractor {

    public record Bbox(double minLat, double minLon, double maxLat, double maxLon) {
        boolean contains(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    /** OSM tag keys kept in the extract (licence-compatible facts useful for the catalogue). */
    static final Set<String> KEEP_KEYS = Set.of(
            "name",
            "name:en",
            "amenity",
            "tourism",
            "historic",
            "natural",
            "leisure",
            "boundary",
            "waterway",
            "cuisine",
            "opening_hours",
            "fee",
            "charge",
            "website",
            "contact:website",
            "wikidata",
            "wikipedia",
            "wheelchair",
            "description",
            "addr:city",
            "addr:street",
            "addr:postcode",
            "place",
            "sport",
            "shop",
            "religion",
            "denomination",
            "heritage",
            "ruins",
            "access",
            "check_date",
            "start_date",
            "phone");

    private record WayData(long id, long[] refs, Map<String, String> tags) {}

    private record RelData(long id, List<Long> outerWays, List<Long> innerWays, Map<String, String> tags) {}

    private OsmPbfExtractor() {}

    public static OsmExtract extract(Path pbf, Bbox bbox, String boundaryName, String adminLevel) throws IOException {
        // Pass 1: candidate ways and relations (incl. boundary).
        List<WayData> candWays = new ArrayList<>();
        List<RelData> candRels = new ArrayList<>();
        RelData[] boundary = new RelData[1];
        String[] timestamp = new String[1];
        run(pbf, new Handler() {
            @Override
            protected void parse(Osmformat.HeaderBlock h) {
                if (h.hasOsmosisReplicationTimestamp())
                    timestamp[0] = Instant.ofEpochSecond(h.getOsmosisReplicationTimestamp())
                            .toString();
            }

            @Override
            protected void parseWays(List<Osmformat.Way> ways) {
                for (Osmformat.Way w : ways) {
                    Map<String, String> tags = tags(w.getKeysList(), w.getValsList());
                    if (OsmCategoryMapper.isCandidate(tags)) candWays.add(new WayData(w.getId(), refs(w), keep(tags)));
                }
            }

            @Override
            protected void parseRelations(List<Osmformat.Relation> rels) {
                for (Osmformat.Relation r : rels) {
                    Map<String, String> tags = tags(r.getKeysList(), r.getValsList());
                    boolean isBoundary = "administrative".equals(tags.get("boundary"))
                            && adminLevel.equals(tags.get("admin_level"))
                            && boundaryName.equals(tags.get("name"));
                    boolean isPoi = OsmCategoryMapper.isCandidate(tags);
                    if (!isBoundary && !isPoi) continue;
                    List<Long> outer = new ArrayList<>(), inner = new ArrayList<>();
                    long mid = 0;
                    for (int i = 0; i < r.getMemidsCount(); i++) {
                        mid += r.getMemids(i);
                        if (r.getTypes(i) != Osmformat.Relation.MemberType.WAY) continue;
                        String role = getStringById(r.getRolesSid(i));
                        (role.equals("inner") ? inner : outer).add(mid);
                    }
                    RelData rd = new RelData(r.getId(), outer, inner, keep(tags));
                    if (isBoundary) boundary[0] = rd;
                    else candRels.add(rd);
                }
            }
        });
        if (boundary[0] == null)
            throw new IllegalStateException("boundary relation not found: " + boundaryName + " level " + adminLevel);

        // Pass 2: node lists of ways referenced by relations.
        Set<Long> memberWayIds = new HashSet<>();
        memberWayIds.addAll(boundary[0].outerWays());
        memberWayIds.addAll(boundary[0].innerWays());
        for (RelData r : candRels) memberWayIds.addAll(r.outerWays());
        Map<Long, long[]> memberWays = new HashMap<>();
        run(pbf, new Handler() {
            @Override
            protected void parseWays(List<Osmformat.Way> ways) {
                for (Osmformat.Way w : ways) if (memberWayIds.contains(w.getId())) memberWays.put(w.getId(), refs(w));
            }
        });

        // Pass 3: nodes.
        long[] needed = neededNodeIds(candWays, memberWays);
        double[] nLat = new double[needed.length], nLon = new double[needed.length];
        Arrays.fill(nLat, Double.NaN);
        List<OsmExtract.Feature> features = new ArrayList<>();
        List<OsmExtract.Feature> settlements = new ArrayList<>();
        run(pbf, new Handler() {
            @Override
            protected void parseDense(Osmformat.DenseNodes d) {
                long id = 0, lat = 0, lon = 0;
                int kv = 0;
                for (int i = 0; i < d.getIdCount(); i++) {
                    id += d.getId(i);
                    lat += d.getLat(i);
                    lon += d.getLon(i);
                    Map<String, String> tags = new HashMap<>();
                    if (d.getKeysValsCount() > 0) {
                        while (d.getKeysVals(kv) != 0) {
                            tags.put(getStringById(d.getKeysVals(kv)), getStringById(d.getKeysVals(kv + 1)));
                            kv += 2;
                        }
                        kv++;
                    }
                    node(id, parseLat(lat), parseLon(lon), tags);
                }
            }

            @Override
            protected void parseNodes(List<Osmformat.Node> nodes) {
                for (Osmformat.Node n : nodes)
                    node(n.getId(), parseLat(n.getLat()), parseLon(n.getLon()), tags(n.getKeysList(), n.getValsList()));
            }

            private void node(long id, double lat, double lon, Map<String, String> tags) {
                int idx = Arrays.binarySearch(needed, id);
                if (idx >= 0) {
                    nLat[idx] = lat;
                    nLon[idx] = lon;
                }
                if (tags.isEmpty() || !bbox.contains(lat, lon)) return;
                if (OsmCategoryMapper.isSettlement(tags))
                    settlements.add(new OsmExtract.Feature("node", id, lat, lon, keep(tags)));
                else if (OsmCategoryMapper.isCandidate(tags))
                    features.add(new OsmExtract.Feature("node", id, lat, lon, keep(tags)));
            }
        });

        for (WayData w : candWays) {
            double[] c = centroid(w.refs(), needed, nLat, nLon);
            if (c != null && bbox.contains(c[0], c[1]))
                features.add(new OsmExtract.Feature("way", w.id(), c[0], c[1], w.tags()));
        }
        for (RelData r : candRels) {
            List<Long> refs = new ArrayList<>();
            for (long wid : r.outerWays()) {
                long[] wr = memberWays.get(wid);
                if (wr != null) for (long x : wr) refs.add(x);
            }
            double[] c = centroid(refs.stream().mapToLong(Long::longValue).toArray(), needed, nLat, nLon);
            if (c != null && bbox.contains(c[0], c[1]))
                features.add(new OsmExtract.Feature("relation", r.id(), c[0], c[1], r.tags()));
        }

        List<double[][]> outers = rings(boundary[0].outerWays(), memberWays, needed, nLat, nLon);
        List<double[][]> inners = rings(boundary[0].innerWays(), memberWays, needed, nLat, nLon);
        if (outers.isEmpty()) throw new IllegalStateException("boundary has no closed outer ring");
        return new OsmExtract(
                pbf.getFileName().toString(), timestamp[0], boundaryName, outers, inners, features, settlements);
    }

    private static long[] neededNodeIds(List<WayData> ways, Map<Long, long[]> memberWays) {
        int total = 0;
        for (WayData w : ways) total += w.refs().length;
        for (long[] r : memberWays.values()) total += r.length;
        long[] all = new long[total];
        int k = 0;
        for (WayData w : ways) for (long x : w.refs()) all[k++] = x;
        for (long[] r : memberWays.values()) for (long x : r) all[k++] = x;
        Arrays.sort(all);
        return Arrays.stream(all).distinct().toArray();
    }

    private static double[] centroid(long[] refs, long[] needed, double[] lat, double[] lon) {
        double sLat = 0, sLon = 0;
        int n = 0;
        int len = refs.length > 1 && refs[0] == refs[refs.length - 1] ? refs.length - 1 : refs.length;
        for (int i = 0; i < len; i++) {
            int idx = Arrays.binarySearch(needed, refs[i]);
            if (idx < 0 || Double.isNaN(lat[idx])) continue;
            sLat += lat[idx];
            sLon += lon[idx];
            n++;
        }
        return n == 0 ? null : new double[] {sLat / n, sLon / n};
    }

    private static List<double[][]> rings(
            List<Long> wayIds, Map<Long, long[]> memberWays, long[] needed, double[] lat, double[] lon) {
        List<long[]> parts = new ArrayList<>();
        for (long id : wayIds) if (memberWays.containsKey(id)) parts.add(memberWays.get(id));
        List<double[][]> out = new ArrayList<>();
        for (long[] ring : RingAssembler.assemble(parts)) {
            double[][] coords = new double[ring.length][];
            boolean ok = true;
            for (int i = 0; i < ring.length; i++) {
                int idx = Arrays.binarySearch(needed, ring[i]);
                if (idx < 0 || Double.isNaN(lat[idx])) {
                    ok = false;
                    break;
                }
                coords[i] = new double[] {lat[idx], lon[idx]};
            }
            if (ok) out.add(coords);
        }
        return out;
    }

    private static Map<String, String> keep(Map<String, String> tags) {
        Map<String, String> out = new LinkedHashMap<>();
        tags.entrySet().stream()
                .filter(e -> KEEP_KEYS.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static long[] refs(Osmformat.Way w) {
        long[] out = new long[w.getRefsCount()];
        long id = 0;
        for (int i = 0; i < out.length; i++) {
            id += w.getRefs(i);
            out[i] = id;
        }
        return out;
    }

    /** Every named node with {@code place} in {@code kinds}, in the whole file (one pass, node tags only). */
    public static List<OsmExtract.Feature> places(Path pbf, Set<String> kinds) throws IOException {
        List<OsmExtract.Feature> out = new ArrayList<>();
        run(pbf, new Handler() {
            @Override
            protected void parseDense(Osmformat.DenseNodes d) {
                long id = 0, lat = 0, lon = 0;
                int kv = 0;
                for (int i = 0; i < d.getIdCount(); i++) {
                    id += d.getId(i);
                    lat += d.getLat(i);
                    lon += d.getLon(i);
                    Map<String, String> tags = new HashMap<>();
                    if (d.getKeysValsCount() > 0) {
                        while (d.getKeysVals(kv) != 0) {
                            tags.put(getStringById(d.getKeysVals(kv)), getStringById(d.getKeysVals(kv + 1)));
                            kv += 2;
                        }
                        kv++;
                    }
                    if (tags.containsKey("name") && kinds.contains(tags.getOrDefault("place", "")))
                        out.add(new OsmExtract.Feature("node", id, parseLat(lat), parseLon(lon), tags));
                }
            }

            @Override
            protected void parseNodes(List<Osmformat.Node> nodes) {
                for (Osmformat.Node n : nodes) {
                    Map<String, String> tags = tags(n.getKeysList(), n.getValsList());
                    if (tags.containsKey("name") && kinds.contains(tags.getOrDefault("place", "")))
                        out.add(new OsmExtract.Feature(
                                "node", n.getId(), parseLat(n.getLat()), parseLon(n.getLon()), tags));
                }
            }
        });
        return out;
    }

    private static void run(Path pbf, Handler h) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(pbf), 1 << 20)) {
            new BlockInputStream(in, h).process();
        }
    }

    /** Base handler with no-op callbacks. */
    private abstract static class Handler extends BinaryParser {
        @Override
        protected void parseRelations(List<Osmformat.Relation> rels) {}

        @Override
        protected void parseDense(Osmformat.DenseNodes nodes) {}

        @Override
        protected void parseNodes(List<Osmformat.Node> nodes) {}

        @Override
        protected void parseWays(List<Osmformat.Way> ways) {}

        @Override
        protected void parse(Osmformat.HeaderBlock header) {}

        @Override
        public void complete() {}

        Map<String, String> tags(List<Integer> keys, List<Integer> vals) {
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) m.put(getStringById(keys.get(i)), getStringById(vals.get(i)));
            return m;
        }
    }
}
