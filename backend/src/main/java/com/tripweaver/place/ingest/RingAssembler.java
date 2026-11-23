package com.tripweaver.place.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * Joins OSM way node-id sequences into closed rings (for multipolygon boundaries). Ways may be reversed.
 * Fragments that cannot be closed are dropped. O(w²) for w ways, fine for one boundary relation.
 */
public final class RingAssembler {

    private RingAssembler() {}

    public static List<long[]> assemble(List<long[]> ways) {
        List<List<Long>> open = new ArrayList<>();
        List<long[]> rings = new ArrayList<>();
        for (long[] w : ways) {
            if (w.length < 2) continue;
            List<Long> seq = new ArrayList<>(w.length);
            for (long id : w) seq.add(id);
            open.add(seq);
        }
        while (!open.isEmpty()) {
            List<Long> cur = open.removeLast();
            boolean grew = true;
            while (!cur.getFirst().equals(cur.getLast()) && grew) {
                grew = false;
                for (int i = 0; i < open.size(); i++) {
                    List<Long> o = open.get(i);
                    Long end = cur.getLast();
                    if (o.getFirst().equals(end)) {
                        cur.addAll(o.subList(1, o.size()));
                    } else if (o.getLast().equals(end)) {
                        List<Long> rev = new ArrayList<>(o.reversed());
                        cur.addAll(rev.subList(1, rev.size()));
                    } else continue;
                    open.remove(i);
                    grew = true;
                    break;
                }
            }
            if (cur.getFirst().equals(cur.getLast()) && cur.size() >= 4) {
                rings.add(cur.stream().mapToLong(Long::longValue).toArray());
            }
        }
        return rings;
    }
}
