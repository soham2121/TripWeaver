package com.tripweaver.algorithms.setcover;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A place that can be selected. {@code cost} is the positive scalar selection cost (normalised mix of
 * duration, spend and detour); hard time/money limits are checked elsewhere.
 */
public record SetCoverCandidate(String id, Set<String> tags, double cost) {

    public SetCoverCandidate {
        Objects.requireNonNull(id, "id");
        tags = Set.copyOf(tags);
    }

    /** Tags normalised with {@link SetCoverInstance#normaliseTag(String)}, duplicates merged. */
    public Set<String> normalisedTags() {
        Set<String> out = new TreeSet<>();
        for (String t : tags) {
            String n = SetCoverInstance.normaliseTag(t);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }
}
