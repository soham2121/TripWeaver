package com.tripweaver.search;

import com.tripweaver.algorithms.trie.Trie;
import com.tripweaver.common.Geo;
import com.tripweaver.place.PlaceSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable in-memory search index over the active catalogue, backed by the assessed {@link Trie}.
 *
 * <p>Indexed terms: each place name plus every suffix that starts at a word boundary (so "aguada" finds
 * "Fort Aguada"); categories; interests; cuisines; neighbourhoods; regions. Nearby search is a linear
 * haversine scan, fine for a few thousand places.
 */
public final class PlaceSearchIndex {

    /** Trie payload: what a term points at. */
    record Ref(String type, String value, UUID placeId) {}

    public record Nearby(PlaceSummary place, double distanceKm) {}

    private static final Comparator<PlaceSummary> BY_NAME =
            Comparator.comparing((PlaceSummary p) -> Trie.normalize(p.name())).thenComparing(p -> p.id());

    private final Trie<Ref> trie;
    private final Map<UUID, PlaceSummary> byId;
    private final List<PlaceSummary> sorted;
    private final int terms;

    private PlaceSearchIndex(Trie<Ref> trie, Map<UUID, PlaceSummary> byId, List<PlaceSummary> sorted, int terms) {
        this.trie = trie;
        this.byId = byId;
        this.sorted = sorted;
        this.terms = terms;
    }

    public static PlaceSearchIndex build(List<PlaceSummary> places) {
        Trie<Ref> t = new Trie<>();
        Map<UUID, PlaceSummary> byId = new HashMap<>();
        for (PlaceSummary p : places) {
            byId.put(p.id(), p);
            Ref ref = new Ref("PLACE", p.name(), p.id());
            for (String term : wordSuffixes(p.name())) t.insert(term, ref);
            for (String c : p.categories()) {
                if (c.startsWith("cuisine:")) {
                    String v = c.substring("cuisine:".length());
                    t.insert(v.replace('_', ' '), new Ref("CUISINE", c, null));
                } else {
                    t.insert(c.replace('_', ' '), new Ref("CATEGORY", c, null));
                }
            }
            for (String i : p.interests()) t.insert(i.replace('_', ' '), new Ref("INTEREST", i, null));
            if (p.neighbourhood() != null)
                t.insert(p.neighbourhood(), new Ref("NEIGHBOURHOOD", p.neighbourhood(), null));
            if (p.regionSlug() != null && p.regionName() != null)
                t.insert(p.regionName(), new Ref("REGION", p.regionSlug(), null));
        }
        List<PlaceSummary> sorted = new ArrayList<>(places);
        sorted.sort(BY_NAME);
        return new PlaceSearchIndex(t, Map.copyOf(byId), List.copyOf(sorted), t.size());
    }

    /** Full name and every suffix starting at a later word. */
    static List<String> wordSuffixes(String name) {
        List<String> out = new ArrayList<>();
        String[] words = name.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String s = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            if (!Trie.normalize(s).isEmpty()) out.add(s);
        }
        return out;
    }

    public List<PlaceSummary> search(PlaceQuery q) {
        Predicate<PlaceSummary> f = p -> (q.category() == null || p.categories().contains(q.category()))
                && (q.interest() == null || p.interests().contains(q.interest()))
                && (q.region() == null || q.region().equals(p.regionSlug()))
                && (!q.activitiesOnly() || p.activity());
        List<PlaceSummary> hits;
        if (q.q() == null || Trie.normalize(q.q()).isEmpty()) {
            hits = sorted.stream().filter(f).toList();
        } else {
            Set<UUID> ids = new LinkedHashSet<>();
            for (Trie.Match<Ref> m : trie.searchPrefix(
                    q.q(), Integer.MAX_VALUE, r -> r.type().equals("PLACE") && f.test(byId.get(r.placeId())))) {
                ids.add(m.ref().placeId());
            }
            hits = ids.stream().map(byId::get).sorted(BY_NAME).toList();
        }
        int from = Math.min(q.offset(), hits.size());
        return hits.subList(from, Math.min(hits.size(), from + q.limit()));
    }

    /** Up to {@code limit} distinct suggestions in trie (key) order. */
    public List<Suggestion> autocomplete(String prefix, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        List<Suggestion> out = new ArrayList<>();
        Set<Ref> seen = new LinkedHashSet<>();
        int fetch = limit * 4;
        List<Suggestion> places = new ArrayList<>();
        for (Trie.Match<Ref> m : trie.searchPrefix(prefix, fetch)) {
            if (!seen.add(m.ref())) continue;
            Ref r = m.ref();
            if (r.type().equals("PLACE")) {
                PlaceSummary p = byId.get(r.placeId());
                String detail = p.primaryCategory() + (p.neighbourhood() != null ? " · " + p.neighbourhood() : "");
                places.add(new Suggestion("PLACE", p.name(), p.id().toString(), p.id(), detail));
            } else {
                out.add(new Suggestion(
                        r.type(), m.display(), r.value(), null, r.type().toLowerCase()));
            }
        }
        // Facets (area, interest, category, cuisine) first: they widen the search; then places.
        out.addAll(places);
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    public List<Nearby> nearby(double lat, double lon, double radiusKm, Set<String> categories, int limit) {
        if (!(radiusKm > 0) || radiusKm > 200) throw new IllegalArgumentException("radiusKm must be in (0, 200]");
        List<Nearby> out = new ArrayList<>();
        for (PlaceSummary p : sorted) {
            if (!categories.isEmpty() && categories.stream().noneMatch(p.categories()::contains)) continue;
            double d = Geo.haversineKm(lat, lon, p.lat(), p.lon());
            if (d <= radiusKm) out.add(new Nearby(p, d));
        }
        out.sort(Comparator.comparingDouble(Nearby::distanceKm));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    public PlaceSummary get(UUID id) {
        return byId.get(id);
    }

    public List<PlaceSummary> all() {
        return sorted;
    }

    public int placeCount() {
        return byId.size();
    }

    public int indexedTerms() {
        return terms;
    }
}
