package com.tripweaver.algorithms.trie;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Prefix tree over normalised terms (Unit 4, text search). Each term may map to several references.
 *
 * <p>Keys are normalised with {@link #normalize(String)}: NFKD decomposition, combining marks removed,
 * lower-cased with {@link Locale#ROOT}, whitespace collapsed and trimmed. Edges are Unicode code points,
 * so non-ASCII and supplementary characters work. The original display spelling is kept per entry.
 *
 * <p>Result order: normalised key in code-point order (a key before its extensions), then insertion
 * order for entries sharing a key. Children are kept in a {@link TreeMap} to make this deterministic.
 *
 * <p>Complexity: insert/delete/containsExact O(m log σ) for key length m and branching σ; searchPrefix
 * O(m log σ + visited subtree) where the walk stops after {@code limit} accepted matches. Space is
 * proportional to the number of distinct key code points plus the number of entries.
 */
public final class Trie<R> {

    /** One search result: original spelling, normalised key and the stored reference. */
    public record Match<R>(String display, String key, R ref) {}

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final class Node<R> {
        final TreeMap<Integer, Node<R>> children = new TreeMap<>();
        final List<Match<R>> entries = new ArrayList<>(0);
    }

    private final Node<R> root = new Node<>();
    private int size;

    /** Canonical search key. Same function is used for indexing and for queries. */
    public static String normalize(String s) {
        if (s == null) return "";
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFKD);
        String noMarks = MARKS.matcher(decomposed).replaceAll("");
        return SPACES.matcher(noMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** Adds (term, ref). Re-inserting an existing pair is a no-op. */
    public void insert(String term, R ref) {
        Objects.requireNonNull(ref, "ref");
        String key = normalize(term);
        if (key.isEmpty()) throw new IllegalArgumentException("term is blank after normalisation");
        Node<R> node = root;
        for (int i = 0; i < key.length(); ) {
            int cp = key.codePointAt(i);
            node = node.children.computeIfAbsent(cp, c -> new Node<>());
            i += Character.charCount(cp);
        }
        for (Match<R> m : node.entries) if (m.ref().equals(ref)) return;
        node.entries.add(new Match<>(term, key, ref));
        size++;
    }

    /** True when some entry has exactly this normalised key. */
    public boolean containsExact(String term) {
        Node<R> n = find(normalize(term));
        return n != null && !n.entries.isEmpty();
    }

    /** Removes (term, ref) and prunes empty nodes. Returns whether something was removed. */
    public boolean delete(String term, R ref) {
        String key = normalize(term);
        if (key.isEmpty()) return false;
        Deque<Node<R>> path = new ArrayDeque<>();
        Deque<Integer> edges = new ArrayDeque<>();
        Node<R> node = root;
        for (int i = 0; i < key.length(); ) {
            int cp = key.codePointAt(i);
            path.push(node);
            edges.push(cp);
            node = node.children.get(cp);
            if (node == null) return false;
            i += Character.charCount(cp);
        }
        if (!node.entries.removeIf(m -> m.ref().equals(ref))) return false;
        size--;
        while (!path.isEmpty() && node.entries.isEmpty() && node.children.isEmpty()) {
            Node<R> parent = path.pop();
            parent.children.remove(edges.pop());
            node = parent;
        }
        return true;
    }

    public List<Match<R>> searchPrefix(String prefix, int limit) {
        return searchPrefix(prefix, limit, r -> true);
    }

    /**
     * Up to {@code limit} entries whose key starts with the normalised prefix and whose reference passes
     * {@code filter}. A blank prefix returns nothing.
     */
    public List<Match<R>> searchPrefix(String prefix, int limit, Predicate<? super R> filter) {
        if (limit < 0) throw new IllegalArgumentException("limit must be >= 0");
        String key = normalize(prefix);
        List<Match<R>> out = new ArrayList<>();
        if (limit == 0 || key.isEmpty()) return out;
        Node<R> start = find(key);
        if (start == null) return out;
        // Iterative pre-order DFS; children pushed in reverse so smallest code point pops first.
        Deque<Node<R>> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            Node<R> n = stack.pop();
            for (Match<R> m : n.entries) {
                if (filter.test(m.ref())) {
                    out.add(m);
                    if (out.size() == limit) return out;
                }
            }
            for (Map.Entry<Integer, Node<R>> e : n.children.descendingMap().entrySet()) stack.push(e.getValue());
        }
        return out;
    }

    /** Number of stored (term, ref) entries. */
    public int size() {
        return size;
    }

    private Node<R> find(String key) {
        Node<R> node = root;
        for (int i = 0; i < key.length() && node != null; ) {
            int cp = key.codePointAt(i);
            node = node.children.get(cp);
            i += Character.charCount(cp);
        }
        return node;
    }
}
