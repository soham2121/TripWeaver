package com.tripweaver.algorithms.trie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Baseline for {@link Trie}: stores entries in a list and scans all of them per query. O(N·m) per search.
 * Returns the same order as the trie: key in code-point order, then insertion order.
 */
public final class LinearScanIndex<R> {

    private final List<Trie.Match<R>> entries = new ArrayList<>();
    private final java.util.Set<java.util.List<Object>> keys =
            new java.util.HashSet<>(); // O(1) duplicate check on insert

    public void insert(String term, R ref) {
        String key = Trie.normalize(term);
        if (key.isEmpty()) throw new IllegalArgumentException("term is blank after normalisation");
        if (!keys.add(java.util.List.of(key, ref))) return;
        entries.add(new Trie.Match<>(term, key, ref));
    }

    public List<Trie.Match<R>> searchPrefix(String prefix, int limit) {
        return searchPrefix(prefix, limit, r -> true);
    }

    public List<Trie.Match<R>> searchPrefix(String prefix, int limit, Predicate<? super R> filter) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        String key = Trie.normalize(prefix);
        if (limit == 0 || key.isEmpty()) return List.of();
        List<Trie.Match<R>> hits = new ArrayList<>();
        for (Trie.Match<R> m : entries) if (m.key().startsWith(key) && filter.test(m.ref())) hits.add(m);
        hits.sort(Comparator.comparing(Trie.Match::key, LinearScanIndex::compareCodePoints)); // stable
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    static int compareCodePoints(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i), cb = b.codePointAt(j);
            if (ca != cb) return Integer.compare(ca, cb);
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
