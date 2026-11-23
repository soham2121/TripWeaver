package com.tripweaver.algorithms.heap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Array-backed binary max heap with an id → position index (Unit 2). Supports priority updates.
 *
 * <p>Order: higher priority first; equal priorities ordered by {@code idOrder} ascending, so extraction
 * order is deterministic.
 *
 * <p>Complexity: insert, extractMax, updatePriority O(log n); peekMax, contains, priorityOf O(1)
 * expected; {@link #buildFrom} O(n) bottom-up. Space O(n).
 */
public final class IndexedMaxHeap<K> {

    public record Entry<K>(K id, double priority) {}

    private final List<Entry<K>> a = new ArrayList<>();
    private final Map<K, Integer> pos = new HashMap<>();
    private final Comparator<? super K> idOrder;
    private long siftSteps;

    public IndexedMaxHeap(Comparator<? super K> idOrder) {
        this.idOrder = idOrder;
    }

    /** Bottom-up heap construction (Floyd), O(n). */
    public static <K> IndexedMaxHeap<K> buildFrom(Map<K, Double> priorities, Comparator<? super K> idOrder) {
        IndexedMaxHeap<K> h = new IndexedMaxHeap<>(idOrder);
        for (var e : priorities.entrySet()) {
            checkPriority(e.getValue());
            h.pos.put(e.getKey(), h.a.size());
            h.a.add(new Entry<>(e.getKey(), e.getValue()));
        }
        for (int i = h.a.size() / 2 - 1; i >= 0; i--) h.siftDown(i);
        return h;
    }

    public void insert(K id, double priority) {
        checkPriority(priority);
        if (pos.containsKey(id)) throw new IllegalArgumentException("duplicate id: " + id);
        a.add(new Entry<>(id, priority));
        pos.put(id, a.size() - 1);
        siftUp(a.size() - 1);
    }

    public Entry<K> peekMax() {
        if (a.isEmpty()) throw new NoSuchElementException("heap is empty");
        return a.getFirst();
    }

    public Entry<K> extractMax() {
        if (a.isEmpty()) throw new NoSuchElementException("heap is empty");
        Entry<K> top = a.getFirst();
        Entry<K> last = a.removeLast();
        pos.remove(top.id());
        if (!a.isEmpty()) {
            a.set(0, last);
            pos.put(last.id(), 0);
            siftDown(0);
        }
        return top;
    }

    public void updatePriority(K id, double priority) {
        checkPriority(priority);
        Integer i = pos.get(id);
        if (i == null) throw new NoSuchElementException("unknown id: " + id);
        a.set(i, new Entry<>(id, priority));
        siftUp(i);
        siftDown(pos.get(id));
    }

    public boolean contains(K id) {
        return pos.containsKey(id);
    }

    public double priorityOf(K id) {
        Integer i = pos.get(id);
        if (i == null) throw new NoSuchElementException("unknown id: " + id);
        return a.get(i).priority();
    }

    public int size() {
        return a.size();
    }

    public boolean isEmpty() {
        return a.isEmpty();
    }

    /** Total sift swaps performed; exposed for the algorithm inspector and benchmarks. */
    public long siftSteps() {
        return siftSteps;
    }

    /** Heap-order and index consistency check, O(n). Used by tests. */
    boolean invariantHolds() {
        for (int i = 0; i < a.size(); i++) {
            if (!pos.get(a.get(i).id()).equals(i)) return false;
            int l = 2 * i + 1, r = l + 1;
            if (l < a.size() && before(l, i)) return false;
            if (r < a.size() && before(r, i)) return false;
        }
        return pos.size() == a.size();
    }

    private static void checkPriority(double p) {
        if (Double.isNaN(p)) throw new IllegalArgumentException("priority must not be NaN");
    }

    /** True when entry at i must sit above entry at j. */
    private boolean before(int i, int j) {
        Entry<K> x = a.get(i), y = a.get(j);
        if (x.priority() != y.priority()) return x.priority() > y.priority();
        return idOrder.compare(x.id(), y.id()) < 0;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (!before(i, p)) return;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        int n = a.size();
        while (true) {
            int l = 2 * i + 1, r = l + 1, best = i;
            if (l < n && before(l, best)) best = l;
            if (r < n && before(r, best)) best = r;
            if (best == i) return;
            swap(i, best);
            i = best;
        }
    }

    private void swap(int i, int j) {
        Entry<K> x = a.get(i), y = a.get(j);
        a.set(i, y);
        a.set(j, x);
        pos.put(y.id(), i);
        pos.put(x.id(), j);
        siftSteps++;
    }
}
