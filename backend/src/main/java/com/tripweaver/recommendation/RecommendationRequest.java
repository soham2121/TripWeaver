package com.tripweaver.recommendation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inputs for candidate selection. Nullable fields mean "not specified".
 *
 * @param interests requested interest → importance weight in (0, 5]
 * @param baseLat accommodation / start location used for the distance score
 * @param travelMonth 1..12; places with a curated seasonal closure in that month are hard-excluded
 * @param maxEntryCostPerPerson soft per-activity spend preference (INR)
 * @param dayMinutes soft: available minutes per day, used for duration fit
 * @param mustVisit hard: always selected (or the request is infeasible)
 * @param avoid hard: never selected
 * @param requireWheelchair hard: excludes places tagged wheelchair=no
 * @param regions hard: restrict to these region slugs (empty = all)
 * @param poolSize K for Quickselect pruning (default 60)
 * @param maxSelections Set Cover greedy pick limit (default 20)
 * @param quotaPerInterest Quickselect slots reserved per requested interest, capped at K/|interests| (default 3)
 * @param seed Quickselect seed (default 42)
 */
public record RecommendationRequest(
        String destination,
        Map<String, Double> interests,
        Double baseLat,
        Double baseLon,
        Integer travelMonth,
        Double maxEntryCostPerPerson,
        Integer dayMinutes,
        Set<UUID> mustVisit,
        Set<UUID> avoid,
        Boolean requireWheelchair,
        Set<String> regions,
        Integer poolSize,
        Integer maxSelections,
        Integer quotaPerInterest,
        Long seed) {

    public static final int DEFAULT_POOL = 60;
    public static final int DEFAULT_MAX_SELECTIONS = 20;
    public static final long DEFAULT_SEED = 42L;
    public static final int DEFAULT_QUOTA = 3;

    public RecommendationRequest {
        interests = interests == null ? Map.of() : Map.copyOf(interests);
        mustVisit = mustVisit == null ? Set.of() : Set.copyOf(mustVisit);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
        regions = regions == null ? Set.of() : Set.copyOf(regions);
        requireWheelchair = requireWheelchair != null && requireWheelchair;
    }

    public int pool() {
        return poolSize == null ? DEFAULT_POOL : poolSize;
    }

    public int selections() {
        return maxSelections == null ? DEFAULT_MAX_SELECTIONS : maxSelections;
    }

    public int quota() {
        return quotaPerInterest == null ? DEFAULT_QUOTA : quotaPerInterest;
    }

    public long seedOrDefault() {
        return seed == null ? DEFAULT_SEED : seed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.destination = destination;
        b.interests = interests;
        b.baseLat = baseLat;
        b.baseLon = baseLon;
        b.travelMonth = travelMonth;
        b.maxEntryCostPerPerson = maxEntryCostPerPerson;
        b.dayMinutes = dayMinutes;
        b.mustVisit = mustVisit;
        b.avoid = avoid;
        b.requireWheelchair = requireWheelchair;
        b.regions = regions;
        b.poolSize = poolSize;
        b.maxSelections = maxSelections;
        b.quotaPerInterest = quotaPerInterest;
        b.seed = seed;
        return b;
    }

    public static final class Builder {
        private String destination;
        private Map<String, Double> interests = Map.of();
        private Double baseLat, baseLon, maxEntryCostPerPerson;
        private Integer travelMonth, dayMinutes, poolSize, maxSelections, quotaPerInterest;
        private Set<UUID> mustVisit = Set.of(), avoid = Set.of();
        private boolean requireWheelchair;
        private Set<String> regions = Set.of();
        private Long seed;

        public Builder destination(String v) {
            destination = v;
            return this;
        }

        public Builder interests(Map<String, Double> v) {
            interests = v;
            return this;
        }

        public Builder base(double lat, double lon) {
            baseLat = lat;
            baseLon = lon;
            return this;
        }

        public Builder travelMonth(Integer v) {
            travelMonth = v;
            return this;
        }

        public Builder maxEntryCostPerPerson(Double v) {
            maxEntryCostPerPerson = v;
            return this;
        }

        public Builder dayMinutes(Integer v) {
            dayMinutes = v;
            return this;
        }

        public Builder mustVisit(Set<UUID> v) {
            mustVisit = v;
            return this;
        }

        public Builder avoid(Set<UUID> v) {
            avoid = v;
            return this;
        }

        public Builder requireWheelchair(boolean v) {
            requireWheelchair = v;
            return this;
        }

        public Builder regions(Set<String> v) {
            regions = v;
            return this;
        }

        public Builder poolSize(Integer v) {
            poolSize = v;
            return this;
        }

        public Builder maxSelections(Integer v) {
            maxSelections = v;
            return this;
        }

        public Builder quotaPerInterest(Integer v) {
            quotaPerInterest = v;
            return this;
        }

        public Builder seed(Long v) {
            seed = v;
            return this;
        }

        public RecommendationRequest build() {
            return new RecommendationRequest(
                    destination,
                    interests,
                    baseLat,
                    baseLon,
                    travelMonth,
                    maxEntryCostPerPerson,
                    dayMinutes,
                    mustVisit,
                    avoid,
                    requireWheelchair,
                    regions,
                    poolSize,
                    maxSelections,
                    quotaPerInterest,
                    seed);
        }
    }
}
