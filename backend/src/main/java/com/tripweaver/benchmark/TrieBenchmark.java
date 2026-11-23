package com.tripweaver.benchmark;

import com.tripweaver.algorithms.trie.LinearScanIndex;
import com.tripweaver.algorithms.trie.Trie;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Trie vs linear scan for prefix autocomplete (limit 10). Terms are SYNTHETIC place-like names from a fixed
 * seed. Correctness is checked (identical results) before timing.
 */
public final class TrieBenchmark {

    static final String[] SYLLABLES = {
        "ba", "ga", "can", "do", "lim", "ca", "lan", "gu", "te", "an", "ju", "na", "va", "tor", "mor", "jim", "pa",
        "lo", "lem", "col", "agon", "da", "ben", "au", "lim", "ma", "pu", "sa", "pon", "de", "chi", "ra", "fort", "be",
        "ach", "café", "sha", "ck", "ri", "ver", "hill", "ti", "go", "ã", "ço"
    };
    static final String[] SUFFIX = {"Beach", "Fort", "Café", "Shack", "Church", "Temple", "Market", "Falls", "View"};

    private TrieBenchmark() {}

    static List<String> terms(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder();
            int k = 2 + r.nextInt(3);
            for (int j = 0; j < k; j++) sb.append(SYLLABLES[r.nextInt(SYLLABLES.length)]);
            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
            sb.append(' ').append(SUFFIX[r.nextInt(SUFFIX.length)]);
            out.add(sb.toString());
        }
        return out;
    }

    static List<String> queries(List<String> terms, int q, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        List<String> out = new ArrayList<>(q);
        for (int i = 0; i < q; i++) {
            String t = terms.get(r.nextInt(terms.size()));
            out.add(t.substring(0, Math.min(t.length(), 2 + r.nextInt(4))));
        }
        return out;
    }

    public static List<BenchmarkHarness.Sample> run(int[] sizes, int queriesPerRun, int warmup, int reps) {
        List<BenchmarkHarness.Sample> all = new ArrayList<>();
        for (int n : sizes) {
            List<String> terms = terms(n, 20261123L + n);
            List<String> qs = queries(terms, queriesPerRun, 7L + n);
            Trie<Integer> trie = new Trie<>();
            LinearScanIndex<Integer> lin = new LinearScanIndex<>();
            for (int i = 0; i < n; i++) {
                trie.insert(terms.get(i), i);
                lin.insert(terms.get(i), i);
            }
            for (String q : qs.subList(0, Math.min(50, qs.size()))) {
                if (!trie.searchPrefix(q, 10).equals(lin.searchPrefix(q, 10)))
                    throw new IllegalStateException("trie and linear scan disagree for '" + q + "' at n=" + n);
            }
            // Linear scan is O(N) per query: use fewer queries at large N so a run stays short.
            List<String> linQs = n >= 100_000 ? qs.subList(0, Math.max(10, queriesPerRun / 20)) : qs;
            all.addAll(BenchmarkHarness.measure("trie", "trie", n, warmup, reps, () -> {
                long hits = 0;
                for (String q : qs) hits += trie.searchPrefix(q, 10).size();
                return qs.size() + (hits & 0);
            }));
            all.addAll(BenchmarkHarness.measure("trie", "linear-scan", n, warmup, reps, () -> {
                long hits = 0;
                for (String q : linQs) hits += lin.searchPrefix(q, 10).size();
                return linQs.size() + (hits & 0);
            }));
        }
        return all;
    }
}
