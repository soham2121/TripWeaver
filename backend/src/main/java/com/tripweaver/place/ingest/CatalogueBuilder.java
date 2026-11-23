package com.tripweaver.place.ingest;

import com.tripweaver.algorithms.trie.Trie;
import com.tripweaver.common.Geo;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure transformation: OSM extract + curated regions + curated fields → catalogue records and an import
 * report. No I/O, so the whole pipeline is unit-testable and reproducible.
 *
 * <p>Steps: map tags → categories; reject features outside the destination boundary polygon; merge
 * duplicates (same normalised name and primary category within 300 m; area features win over nodes);
 * assign region (nearest anchor settlement) and neighbourhood (nearest settlement within 3 km); attach
 * OSM-provided fee/charge and opening hours with provider labels; overlay curated fields; flag missing data.
 */
public final class CatalogueBuilder {

    static final double MERGE_RADIUS_KM = 0.3;
    static final double NEIGHBOURHOOD_RADIUS_KM = 3.0;
    private static final Pattern CHARGE = Pattern.compile(
            "^\\s*(?:₹|INR|Rs\\.?)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:INR|₹|Rs\\.?|rupees)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final List<String> SETTLEMENT_RANK =
            List.of("city", "town", "suburb", "village", "neighbourhood", "hamlet");

    private CatalogueBuilder() {}

    private record Candidate(OsmExtract.Feature f, CategoryMapping m, String key) {}

    public static BuiltCatalogue build(OsmExtract ex, RegionConfig rc, CuratedData curated) {
        Instant osmTime = ex.sourceTimestamp() == null ? null : Instant.parse(ex.sourceTimestamp());
        Polygon boundary = ex.boundary();
        String destSlug = rc.destination().slug();

        // Regions from anchor settlements.
        List<BuiltCatalogue.Region> regions = new ArrayList<>();
        for (RegionConfig.RegionDef r : rc.regions()) {
            OsmExtract.Feature anchor = ex.settlements().stream()
                    .filter(s -> r.anchor().equals(s.tags().get("name")))
                    .min((a, b) -> Integer.compare(rank(a), rank(b)))
                    .orElseThrow(() -> new IllegalStateException("region anchor not found in extract: " + r.anchor()));
            regions.add(new BuiltCatalogue.Region(
                    uuid("region:" + destSlug + ":" + r.slug()), r.slug(), r.name(), anchor.lat(), anchor.lon()));
        }

        // Map + boundary filter.
        Map<String, Integer> rejected = new TreeMap<>();
        List<Candidate> inside = new ArrayList<>();
        for (OsmExtract.Feature f : ex.features()) {
            var m = OsmCategoryMapper.map(f.tags());
            if (m.isEmpty()) {
                rejected.merge("NOT_A_CANDIDATE", 1, Integer::sum);
            } else if (!boundary.contains(f.lat(), f.lon())) {
                rejected.merge("OUTSIDE_BOUNDARY", 1, Integer::sum);
            } else {
                inside.add(new Candidate(f, m.get(), Trie.normalize(f.tags().get("name"))));
            }
        }

        // Merge duplicates.
        List<List<Candidate>> groups = new ArrayList<>();
        Map<String, List<List<Candidate>>> byKey = new HashMap<>();
        for (Candidate c : inside) {
            String k = c.key() + "|" + c.m().primaryCategory();
            List<Candidate> target = null;
            for (List<Candidate> g : byKey.getOrDefault(k, List.of())) {
                Candidate h = g.getFirst();
                if (Geo.haversineKm(h.f().lat(), h.f().lon(), c.f().lat(), c.f().lon()) <= MERGE_RADIUS_KM) {
                    target = g;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                groups.add(target);
                byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(target);
            }
            target.add(c);
        }

        int merged = 0;
        List<String> mergedExamples = new ArrayList<>();
        List<BuiltPlace> places = new ArrayList<>();
        Map<String, Integer> placeIndexByRef = new HashMap<>();
        for (List<Candidate> g : groups) {
            Candidate keep = g.getFirst();
            for (Candidate c : g) if (better(c.f(), keep.f())) keep = c;
            List<BuiltPlace.Source> extra = new ArrayList<>();
            for (Candidate c : g) {
                if (c == keep) continue;
                merged++;
                if (mergedExamples.size() < 20)
                    mergedExamples.add(c.f().osmRef() + " -> " + keep.f().osmRef());
                extra.add(new BuiltPlace.Source(
                        "identity",
                        "OSM",
                        c.f().osmRef(),
                        osmUrl(c.f()),
                        "PROVIDER_SOURCED",
                        osmTime,
                        "Duplicate mapping merged into " + keep.f().osmRef()));
            }
            BuiltPlace p = place(keep, extra, regions, ex.settlements(), osmTime);
            placeIndexByRef.put(keep.f().osmRef(), places.size());
            places.add(p);
        }

        // Curated overlay.
        List<String> unresolved = new ArrayList<>();
        Instant curatedAt = curated.verifiedAt() == null ? null : Instant.parse(curated.verifiedAt());
        for (CuratedData.Rec r : curated.records()) {
            Integer idx = r.osmRef() != null ? placeIndexByRef.get(r.osmRef()) : findByName(places, r.name());
            if (idx == null) {
                unresolved.add((r.osmRef() == null ? "?" : r.osmRef()) + " " + r.name());
                continue;
            }
            places.set(idx, overlay(places.get(idx), r, curatedAt));
        }

        // Report.
        Map<String, Integer> byCat = new TreeMap<>(), byRegion = new TreeMap<>(), byInterest = new TreeMap<>();
        Map<String, Integer> missing = new TreeMap<>();
        for (BuiltPlace p : places) {
            byCat.merge(p.mapping().primaryCategory(), 1, Integer::sum);
            if (p.regionSlug() != null) byRegion.merge(p.regionSlug(), 1, Integer::sum);
            for (String i : p.mapping().interests()) byInterest.merge(i, 1, Integer::sum);
            for (String mf : p.missing()) missing.merge(mf, 1, Integer::sum);
        }
        double[] bb = bbox(ex.boundaryOuters());
        var dest = new BuiltCatalogue.Destination(
                uuid("destination:" + destSlug), destSlug, rc.destination().name(), bb[0], bb[1], bb[2], bb[3]);
        var report = new BuiltCatalogue.Report(
                ex.sourceFile(),
                ex.sourceTimestamp(),
                ex.features().size(),
                places.size(),
                merged,
                rejected,
                mergedExamples,
                unresolved,
                byCat,
                byRegion,
                byInterest,
                missing);
        return new BuiltCatalogue(dest, regions, places, report);
    }

    private static BuiltPlace place(
            Candidate c,
            List<BuiltPlace.Source> extraSources,
            List<BuiltCatalogue.Region> regions,
            List<OsmExtract.Feature> settlements,
            Instant osmTime) {
        OsmExtract.Feature f = c.f();
        Map<String, String> t = f.tags();
        CategoryMapping m = c.m();

        BuiltCatalogue.Region region = null;
        double best = Double.POSITIVE_INFINITY;
        for (BuiltCatalogue.Region r : regions) {
            double d = Geo.haversineKm(f.lat(), f.lon(), r.lat(), r.lon());
            if (d < best) {
                best = d;
                region = r;
            }
        }
        String neighbourhood = null;
        double bestN = NEIGHBOURHOOD_RADIUS_KM;
        for (OsmExtract.Feature s : settlements) {
            double d = Geo.haversineKm(f.lat(), f.lon(), s.lat(), s.lon());
            if (d <= bestN) {
                bestN = d;
                neighbourhood = s.tags().get("name");
            }
        }

        List<BuiltPlace.Source> sources = new ArrayList<>();
        sources.add(new BuiltPlace.Source(
                "name,location,category", "OSM", f.osmRef(), osmUrl(f), "PROVIDER_SOURCED", osmTime, null));
        if (m.activity()) {
            sources.add(new BuiltPlace.Source(
                    "visit_duration",
                    "TRIPWEAVER",
                    null,
                    null,
                    "CURATED_ESTIMATE",
                    null,
                    "TripWeaver category default for " + m.primaryCategory()));
        }
        sources.addAll(extraSources);

        List<BuiltPlace.Cost> costs = new ArrayList<>();
        String fee = t.get("fee"), charge = t.get("charge");
        Matcher cm = charge == null ? null : CHARGE.matcher(charge);
        if (cm != null && cm.matches()) {
            double v = Double.parseDouble(cm.group(1));
            costs.add(new BuiltPlace.Cost(
                    "ENTRY",
                    v,
                    v,
                    "INR",
                    "PER_PERSON",
                    "PROVIDER_SOURCED",
                    "OSM",
                    osmUrl(f),
                    osmTime,
                    "OSM charge=" + charge));
        } else if ("no".equals(fee)) {
            costs.add(new BuiltPlace.Cost(
                    "ENTRY", 0, 0, "INR", "PER_PERSON", "PROVIDER_SOURCED", "OSM", osmUrl(f), osmTime, "OSM fee=no"));
        } else if (fee != null || charge != null) {
            sources.add(new BuiltPlace.Source(
                    "cost",
                    "OSM",
                    f.osmRef(),
                    osmUrl(f),
                    "UNKNOWN",
                    osmTime,
                    "OSM fee=" + fee + (charge != null ? ", charge=" + charge : "") + " (amount unknown)"));
        }

        List<BuiltPlace.Hours> hours = new ArrayList<>();
        if (t.get("opening_hours") != null) {
            hours.add(new BuiltPlace.Hours(
                    t.get("opening_hours"), "PROVIDER_SOURCED", "OSM", osmUrl(f), osmTime, "OSM opening_hours tag"));
        }

        Set<String> access = new LinkedHashSet<>();
        if (t.get("wheelchair") != null) access.add("wheelchair:" + t.get("wheelchair"));

        String website = t.getOrDefault("website", t.get("contact:website"));
        String description = t.get("description");
        BuiltPlace p = new BuiltPlace(
                uuid("osm:" + f.osmRef()),
                f.type(),
                f.id(),
                t.get("name"),
                description,
                f.lat(),
                f.lon(),
                region == null ? null : region.slug(),
                neighbourhood,
                m,
                Set.copyOf(access),
                website,
                new LinkedHashMap<>(t),
                costs,
                hours,
                null,
                sources,
                null,
                null);
        return withQuality(p);
    }

    private static BuiltPlace overlay(BuiltPlace p, CuratedData.Rec r, Instant at) {
        List<BuiltPlace.Hours> hours = p.hours();
        List<BuiltPlace.Cost> costs = new ArrayList<>(p.costs());
        List<BuiltPlace.Source> sources = new ArrayList<>(p.sources());
        BuiltPlace.Seasonal seasonal = p.seasonal();
        if (r.openingHours() != null) {
            var h = r.openingHours();
            hours = List.of(new BuiltPlace.Hours(
                    h.raw(), h.confidence(), srcType(h.confidence()), h.sourceUrl(), at, h.note()));
            sources.add(new BuiltPlace.Source(
                    "opening_hours", srcType(h.confidence()), null, h.sourceUrl(), h.confidence(), at, h.note()));
        }
        if (r.costs() != null) {
            for (CuratedData.Cost c : r.costs()) {
                costs.removeIf(x -> x.kind().equals(c.kind()));
                costs.add(new BuiltPlace.Cost(
                        c.kind(),
                        c.min(),
                        c.max(),
                        c.currency(),
                        c.basis(),
                        c.confidence(),
                        srcType(c.confidence()),
                        c.sourceUrl(),
                        at,
                        c.note()));
                sources.add(new BuiltPlace.Source(
                        "cost:" + c.kind(),
                        srcType(c.confidence()),
                        null,
                        c.sourceUrl(),
                        c.confidence(),
                        at,
                        c.note()));
            }
        }
        if (r.seasonal() != null) {
            var s = r.seasonal();
            seasonal = new BuiltPlace.Seasonal(s.text(), s.closedMonths(), s.confidence(), s.sourceUrl());
            sources.add(new BuiltPlace.Source(
                    "seasonal", srcType(s.confidence()), null, s.sourceUrl(), s.confidence(), at, s.note()));
        }
        return withQuality(new BuiltPlace(
                p.id(),
                p.osmType(),
                p.osmId(),
                p.name(),
                p.description(),
                p.lat(),
                p.lon(),
                p.regionSlug(),
                p.neighbourhood(),
                p.mapping(),
                p.accessibility(),
                p.website(),
                p.osmTags(),
                List.copyOf(costs),
                List.copyOf(hours),
                seasonal,
                List.copyOf(sources),
                null,
                null));
    }

    private static BuiltPlace withQuality(BuiltPlace p) {
        List<String> missing = new ArrayList<>();
        if (p.mapping().activity()) {
            if (p.costs().isEmpty()) missing.add("cost");
            if (p.hours().isEmpty()) missing.add("opening_hours");
            if (p.description() == null) missing.add("description");
        }
        return new BuiltPlace(
                p.id(),
                p.osmType(),
                p.osmId(),
                p.name(),
                p.description(),
                p.lat(),
                p.lon(),
                p.regionSlug(),
                p.neighbourhood(),
                p.mapping(),
                p.accessibility(),
                p.website(),
                p.osmTags(),
                p.costs(),
                p.hours(),
                p.seasonal(),
                p.sources(),
                missing.isEmpty() ? "COMPLETE" : "PARTIAL",
                List.copyOf(missing));
    }

    private static Integer findByName(List<BuiltPlace> places, String name) {
        String k = Trie.normalize(name);
        for (int i = 0; i < places.size(); i++)
            if (Trie.normalize(places.get(i).name()).equals(k)) return i;
        return null;
    }

    private static String srcType(String confidence) {
        return "VERIFIED".equals(confidence) ? "OFFICIAL" : "CURATED";
    }

    /** relation beats way beats node; then lower id. */
    private static boolean better(OsmExtract.Feature a, OsmExtract.Feature b) {
        int ra = typeRank(a.type()), rb = typeRank(b.type());
        return ra != rb ? ra < rb : a.id() < b.id();
    }

    private static int typeRank(String t) {
        return switch (t) {
            case "relation" -> 0;
            case "way" -> 1;
            default -> 2;
        };
    }

    private static int rank(OsmExtract.Feature s) {
        int i = SETTLEMENT_RANK.indexOf(s.tags().get("place"));
        return i < 0 ? 99 : i;
    }

    static String osmUrl(OsmExtract.Feature f) {
        return "https://www.openstreetmap.org/" + f.osmRef();
    }

    static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static double[] bbox(List<double[][]> rings) {
        double minLat = 90, minLon = 180, maxLat = -90, maxLon = -180;
        for (double[][] r : rings) {
            for (double[] p : r) {
                minLat = Math.min(minLat, p[0]);
                maxLat = Math.max(maxLat, p[0]);
                minLon = Math.min(minLon, p[1]);
                maxLon = Math.max(maxLon, p[1]);
            }
        }
        return new double[] {minLat, minLon, maxLat, maxLon};
    }
}
