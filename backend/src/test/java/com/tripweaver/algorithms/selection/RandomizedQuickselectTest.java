package com.tripweaver.algorithms.selection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomizedQuickselectTest {

    record Item(String id, double score, Set<String> cats) {
        Item(String id, double score) {
            this(id, score, Set.of());
        }
    }

    static final Comparator<Item> BY_ID = Comparator.comparing(Item::id);

    static SelectionResult<Item> top(List<Item> items, int k, long seed) {
        return RandomizedQuickselect.selectTopK(items, Item::score, BY_ID, k, seed);
    }

    /** Reference: full sort by score desc, then id asc. */
    static List<Item> sortRef(List<Item> items, int k) {
        return items.stream()
                .sorted(Comparator.comparingDouble(Item::score).reversed().thenComparing(BY_ID))
                .limit(k)
                .toList();
    }

    static List<Item> randomItems(Random r, int n, int distinctScores) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Item(String.format("p%05d", i), r.nextInt(distinctScores) - 3));
        return out;
    }

    @Test
    void singleElement() {
        SelectionResult<Item> r = top(List.of(new Item("a", 1.5)), 1, 7);
        assertEquals(List.of(new Item("a", 1.5)), r.selected());
        assertEquals(1.5, r.threshold());
        assertEquals(7, r.seed());
    }

    @Test
    void kZeroReturnsEmptyWithNaNThreshold() {
        SelectionResult<Item> r = top(List.of(new Item("a", 1)), 0, 1);
        assertTrue(r.selected().isEmpty());
        assertTrue(Double.isNaN(r.threshold()));
    }

    @Test
    void kEqualsNReturnsAllSortedDescending() {
        List<Item> in = List.of(new Item("a", 1), new Item("b", 3), new Item("c", 2));
        assertEquals(sortRef(in, 3), top(in, 3, 1).selected());
    }

    @Test
    void invalidKRejected() {
        List<Item> in = List.of(new Item("a", 1));
        assertThrows(IllegalArgumentException.class, () -> top(in, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> top(in, 2, 1));
    }

    @Test
    void emptyInputWithKZero() {
        assertTrue(top(List.of(), 0, 1).selected().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> top(List.of(), 1, 1));
    }

    @Test
    void duplicateScoresUseDeterministicIdTieBreak() {
        List<Item> in = new ArrayList<>();
        for (int i = 9; i >= 0; i--) in.add(new Item("p" + i, 5));
        SelectionResult<Item> r = top(in, 3, 99);
        assertEquals(
                List.of("p0", "p1", "p2"), r.selected().stream().map(Item::id).toList());
        assertEquals(5, r.threshold());
    }

    @Test
    void sortedAndReverseSortedInputs() {
        List<Item> asc = new ArrayList<>();
        for (int i = 0; i < 500; i++) asc.add(new Item(String.format("p%03d", i), i));
        List<Item> desc = new ArrayList<>(asc.reversed());
        assertEquals(sortRef(asc, 10), top(asc, 10, 3).selected());
        assertEquals(sortRef(desc, 10), top(desc, 10, 3).selected());
    }

    @Test
    void negativeAndZeroScoresAreOrderedNormally() {
        List<Item> in = List.of(new Item("a", -2), new Item("b", 0), new Item("c", -0.5));
        assertEquals(
                List.of("b", "c"),
                top(in, 2, 1).selected().stream().map(Item::id).toList());
        assertEquals(-0.5, top(in, 2, 1).threshold());
    }

    @Test
    void nonFiniteScoreRejected() {
        List<Item> in = List.of(new Item("a", Double.NaN), new Item("b", 1));
        assertThrows(IllegalArgumentException.class, () -> top(in, 1, 1));
    }

    @Test
    void manySeedsAgreeWithSortingReference() {
        Random r = new Random(2026);
        for (int trial = 0; trial < 200; trial++) {
            int n = 1 + r.nextInt(300);
            List<Item> in = randomItems(r, n, 1 + r.nextInt(20));
            int k = r.nextInt(n + 1);
            long seed = r.nextLong();
            assertEquals(sortRef(in, k), top(in, k, seed).selected(), "trial " + trial);
        }
    }

    @Test
    void inputIsNotMutated() {
        List<Item> in = randomItems(new Random(1), 100, 10);
        List<Item> copy = List.copyOf(in);
        top(new ArrayList<>(in), 30, 5);
        ArrayList<Item> mutable = new ArrayList<>(in);
        top(mutable, 30, 5);
        assertEquals(copy, mutable);
    }

    @Test
    void sameSeedGivesSameStatsAndAnySeedGivesSameSelection() {
        List<Item> in = randomItems(new Random(3), 1000, 50);
        SelectionResult<Item> a = top(in, 25, 11);
        SelectionResult<Item> b = top(in, 25, 11);
        SelectionResult<Item> c = top(in, 25, 12);
        assertEquals(a.partitionRounds(), b.partitionRounds());
        assertEquals(a.selected(), c.selected());
        assertEquals(1000, a.inputSize());
        assertNotNull(a.tiePolicy());
    }

    @Test
    void categoryQuotaPreservesRareInterest() {
        List<Item> in = new ArrayList<>();
        for (int i = 0; i < 50; i++) in.add(new Item(String.format("beach%02d", i), 90 + i % 10, Set.of("beach")));
        in.add(new Item("heritage1", 10, Set.of("heritage")));
        in.add(new Item("heritage2", 20, Set.of("heritage")));

        SelectionResult<Item> plain = top(in, 5, 1);
        assertTrue(plain.selected().stream().noneMatch(i -> i.cats().contains("heritage")));

        CategoryAwareSelection<Item> r = CategoryAwareSelector.select(
                in, Item::score, Item::cats, BY_ID, Map.of("heritage", 1, "beach", 1), 5, 1);
        assertEquals(5, r.selected().size());
        assertTrue(r.selected().stream().anyMatch(i -> i.id().equals("heritage2")));
        assertEquals(
                List.of("heritage2"),
                r.reserved().get("heritage").stream().map(Item::id).toList());
        assertTrue(r.shortfalls().isEmpty());
        assertEquals(5, r.selected().stream().distinct().count());
    }

    @Test
    void categoryQuotaReportsShortfallAndRejectsOversizedQuotas() {
        List<Item> in = List.of(new Item("a", 1, Set.of("food")), new Item("b", 2, Set.of("beach")));
        CategoryAwareSelection<Item> r = CategoryAwareSelector.select(
                in, Item::score, Item::cats, BY_ID, Map.of("nightlife", 1, "food", 1), 2, 1);
        assertEquals(Map.of("nightlife", 1), r.shortfalls());
        assertEquals(2, r.selected().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> CategoryAwareSelector.select(
                        in, Item::score, Item::cats, BY_ID, Map.of("food", 2, "beach", 1), 2, 1));
    }

    @Test
    void itemInTwoQuotaCategoriesIsSelectedOnce() {
        List<Item> in = List.of(
                new Item("both", 9, Set.of("food", "beach")),
                new Item("f", 1, Set.of("food")),
                new Item("x", 5, Set.of()));
        CategoryAwareSelection<Item> r =
                CategoryAwareSelector.select(in, Item::score, Item::cats, BY_ID, Map.of("food", 1, "beach", 1), 2, 4);
        assertEquals(List.of("both", "x"), r.selected().stream().map(Item::id).toList());
    }
}
