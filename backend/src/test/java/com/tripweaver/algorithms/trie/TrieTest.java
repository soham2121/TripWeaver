package com.tripweaver.algorithms.trie;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TrieTest {

    private static List<String> displays(List<Trie.Match<String>> ms) {
        return ms.stream().map(Trie.Match::display).toList();
    }

    @Test
    void emptyTrieReturnsNothing() {
        Trie<String> t = new Trie<>();
        assertTrue(t.searchPrefix("a", 10).isEmpty());
        assertFalse(t.containsExact("a"));
        assertEquals(0, t.size());
    }

    @Test
    void exactTermIsFound() {
        Trie<String> t = new Trie<>();
        t.insert("Fort Aguada", "p1");
        assertTrue(t.containsExact("Fort Aguada"));
        assertFalse(t.containsExact("Fort"));
        assertEquals(
                List.of("p1"),
                t.searchPrefix("fort aguada", 5).stream().map(Trie.Match::ref).toList());
    }

    @Test
    void sharedPrefixReturnsAllMatchesInKeyOrder() {
        Trie<String> t = new Trie<>();
        t.insert("Baga Beach", "b1");
        t.insert("Bambolim Beach", "b2");
        t.insert("Basilica of Bom Jesus", "h1");
        t.insert("Calangute Beach", "b3");
        assertEquals(
                List.of("Baga Beach", "Bambolim Beach", "Basilica of Bom Jesus"), displays(t.searchPrefix("ba", 10)));
    }

    @Test
    void prefixWithNoMatchIsEmpty() {
        Trie<String> t = new Trie<>();
        t.insert("Panaji", "x");
        assertTrue(t.searchPrefix("q", 10).isEmpty());
        assertTrue(t.searchPrefix("panajix", 10).isEmpty());
    }

    @Test
    void caseAndWhitespaceAreNormalisedButDisplayKept() {
        Trie<String> t = new Trie<>();
        t.insert("  Fort   AGUADA ", "p1");
        List<Trie.Match<String>> r = t.searchPrefix("FORT ag", 10);
        assertEquals(1, r.size());
        assertEquals("  Fort   AGUADA ", r.get(0).display());
        assertEquals("fort aguada", r.get(0).key());
    }

    @Test
    void duplicateTermMapsToMultipleRefs() {
        Trie<String> t = new Trie<>();
        t.insert("Beach Shack", "a");
        t.insert("Beach Shack", "b");
        t.insert("Beach Shack", "a"); // same pair again is idempotent
        assertEquals(
                List.of("a", "b"),
                t.searchPrefix("beach", 10).stream().map(Trie.Match::ref).toList());
        assertEquals(2, t.size());
    }

    @Test
    void unicodeAndDiacriticsAreHandled() {
        Trie<String> t = new Trie<>();
        t.insert("Café Mondegar", "c1");
        t.insert("पणजी", "d1");
        t.insert("🏖 Beach Shack", "e1");
        assertEquals(
                List.of("c1"),
                t.searchPrefix("cafe", 5).stream().map(Trie.Match::ref).toList());
        assertEquals(
                List.of("c1"),
                t.searchPrefix("CAFÉ m", 5).stream().map(Trie.Match::ref).toList());
        assertEquals(
                List.of("d1"),
                t.searchPrefix("पण", 5).stream().map(Trie.Match::ref).toList());
        assertEquals(
                List.of("e1"),
                t.searchPrefix("🏖", 5).stream().map(Trie.Match::ref).toList());
    }

    @Test
    void resultLimitIsRespected() {
        Trie<String> t = new Trie<>();
        for (int i = 0; i < 20; i++) t.insert("Cafe " + i, "c" + i);
        assertEquals(5, t.searchPrefix("cafe", 5).size());
        assertTrue(t.searchPrefix("cafe", 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> t.searchPrefix("cafe", -1));
    }

    @Test
    void blankPrefixReturnsNothing() {
        Trie<String> t = new Trie<>();
        t.insert("Anjuna", "a");
        assertTrue(t.searchPrefix("   ", 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> t.insert("  ", "x"));
    }

    @Test
    void deleteThenReinsert() {
        Trie<String> t = new Trie<>();
        t.insert("Anjuna", "a");
        t.insert("Anjuna Flea Market", "m");
        assertTrue(t.delete("ANJUNA", "a"));
        assertFalse(t.delete("Anjuna", "a"));
        assertFalse(t.containsExact("Anjuna"));
        assertTrue(t.containsExact("Anjuna Flea Market"));
        assertEquals(1, t.size());
        t.insert("Anjuna", "a");
        assertEquals(
                List.of("a", "m"),
                t.searchPrefix("anj", 10).stream().map(Trie.Match::ref).toList());
    }

    @Test
    void filterAppliesBeforeLimit() {
        Trie<String> t = new Trie<>();
        t.insert("Goa Fort", "heritage:1");
        t.insert("Goa Beach", "beach:1");
        t.insert("Goa Gajalee", "food:1");
        t.insert("Goa Grill", "food:2");
        List<Trie.Match<String>> r = t.searchPrefix("goa", 1, ref -> ref.startsWith("food"));
        assertEquals(List.of("food:1"), r.stream().map(Trie.Match::ref).toList());
    }

    @Test
    void matchesLinearScanReferenceOnRandomTerms() {
        Random rnd = new Random(42);
        Trie<Integer> t = new Trie<>();
        LinearScanIndex<Integer> ref = new LinearScanIndex<>();
        String alphabet = "abcé ";
        for (int i = 0; i < 2000; i++) {
            StringBuilder sb = new StringBuilder("x");
            int len = 1 + rnd.nextInt(6);
            for (int j = 0; j < len; j++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            t.insert(sb.toString(), i);
            ref.insert(sb.toString(), i);
        }
        List<String> prefixes = new ArrayList<>(List.of("x", "xa", "xe", "xé", "xab", "xc c", "xb", "xzz"));
        for (String p : prefixes) {
            for (int limit : new int[] {1, 7, 50, 5000}) {
                assertEquals(ref.searchPrefix(p, limit), t.searchPrefix(p, limit), "prefix=" + p + " limit=" + limit);
            }
        }
    }
}
