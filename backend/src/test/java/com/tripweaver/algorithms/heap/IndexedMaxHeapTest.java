package com.tripweaver.algorithms.heap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IndexedMaxHeapTest {

    static IndexedMaxHeap<String> heap() {
        return new IndexedMaxHeap<>(Comparator.naturalOrder());
    }

    static List<String> drain(IndexedMaxHeap<String> h) {
        List<String> out = new ArrayList<>();
        while (!h.isEmpty()) {
            out.add(h.extractMax().id());
            assertTrue(h.invariantHolds());
        }
        return out;
    }

    @Test
    void emptyHeap() {
        IndexedMaxHeap<String> h = heap();
        assertTrue(h.isEmpty());
        assertEquals(0, h.size());
        assertThrows(NoSuchElementException.class, h::peekMax);
        assertThrows(NoSuchElementException.class, h::extractMax);
    }

    @Test
    void oneItem() {
        IndexedMaxHeap<String> h = heap();
        h.insert("a", 3);
        assertEquals("a", h.peekMax().id());
        assertEquals(1, h.size());
        assertEquals(new IndexedMaxHeap.Entry<>("a", 3.0), h.extractMax());
        assertTrue(h.isEmpty());
        assertFalse(h.contains("a"));
    }

    @Test
    void ascendingInsertsExtractDescending() {
        IndexedMaxHeap<String> h = heap();
        for (int i = 0; i < 50; i++) {
            h.insert(String.format("k%02d", i), i);
            assertTrue(h.invariantHolds());
        }
        List<String> out = drain(h);
        assertEquals("k49", out.getFirst());
        assertEquals("k00", out.getLast());
    }

    @Test
    void descendingInsertsExtractDescending() {
        IndexedMaxHeap<String> h = heap();
        for (int i = 49; i >= 0; i--) h.insert(String.format("k%02d", i), i);
        List<String> out = drain(h);
        assertEquals("k49", out.getFirst());
        assertEquals(50, out.size());
    }

    @Test
    void duplicatePrioritiesBreakTiesBySmallerId() {
        IndexedMaxHeap<String> h = heap();
        for (String id : List.of("d", "b", "a", "c")) h.insert(id, 1.0);
        assertEquals(List.of("a", "b", "c", "d"), drain(h));
    }

    @Test
    void extractionFollowsPriorityOrder() {
        IndexedMaxHeap<String> h = heap();
        h.insert("x", 2);
        h.insert("y", 9);
        h.insert("z", -1);
        h.insert("w", 9);
        assertEquals(List.of("w", "y", "x", "z"), drain(h));
    }

    @Test
    void increaseAndDecreasePriority() {
        IndexedMaxHeap<String> h = heap();
        h.insert("a", 1);
        h.insert("b", 2);
        h.insert("c", 3);
        h.updatePriority("a", 10);
        assertTrue(h.invariantHolds());
        assertEquals("a", h.peekMax().id());
        h.updatePriority("a", 0);
        assertTrue(h.invariantHolds());
        assertEquals(0.0, h.priorityOf("a"));
        assertEquals(List.of("c", "b", "a"), drain(h));
    }

    @Test
    void updateMissingIdAndDuplicateInsertRejected() {
        IndexedMaxHeap<String> h = heap();
        h.insert("a", 1);
        assertThrows(NoSuchElementException.class, () -> h.updatePriority("zz", 1));
        assertThrows(IllegalArgumentException.class, () -> h.insert("a", 2));
        assertThrows(IllegalArgumentException.class, () -> h.insert("n", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> h.updatePriority("a", Double.NaN));
    }

    @Test
    void bottomUpBuildProducesValidHeap() {
        Map<String, Double> m = new LinkedHashMap<>();
        Random r = new Random(5);
        for (int i = 0; i < 300; i++) m.put("k" + i, (double) r.nextInt(40));
        IndexedMaxHeap<String> h = IndexedMaxHeap.buildFrom(m, Comparator.naturalOrder());
        assertTrue(h.invariantHolds());
        assertEquals(300, h.size());
        List<String> expected = m.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(expected, drain(h));
    }

    @Test
    void randomOperationsMatchNaiveReferenceAndKeepInvariant() {
        Random r = new Random(77);
        IndexedMaxHeap<String> h = heap();
        Map<String, Double> ref = new LinkedHashMap<>();
        for (int step = 0; step < 5000; step++) {
            int op = r.nextInt(4);
            String id = "k" + r.nextInt(200);
            double p = r.nextInt(100);
            switch (op) {
                case 0 -> {
                    if (!ref.containsKey(id)) {
                        h.insert(id, p);
                        ref.put(id, p);
                    }
                }
                case 1 -> {
                    if (ref.containsKey(id)) {
                        h.updatePriority(id, p);
                        ref.put(id, p);
                    }
                }
                case 2 -> {
                    if (!ref.isEmpty()) {
                        String best = naiveMax(ref);
                        assertEquals(best, h.extractMax().id());
                        ref.remove(best);
                    }
                }
                default -> {
                    if (!ref.isEmpty()) assertEquals(naiveMax(ref), h.peekMax().id());
                }
            }
            assertEquals(ref.size(), h.size());
            assertTrue(h.invariantHolds(), "step " + step);
        }
    }

    static String naiveMax(Map<String, Double> m) {
        String best = null;
        for (var e : m.entrySet()) {
            if (best == null
                    || e.getValue() > m.get(best)
                    || (e.getValue().equals(m.get(best)) && e.getKey().compareTo(best) < 0)) best = e.getKey();
        }
        return best;
    }
}
