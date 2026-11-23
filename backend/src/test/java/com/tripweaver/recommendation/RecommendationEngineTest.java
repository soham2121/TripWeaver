package com.tripweaver.recommendation;

import static org.junit.jupiter.api.Assertions.*;

import com.tripweaver.common.ApiException;
import com.tripweaver.place.PlaceSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecommendationEngineTest {

    static UUID id(String n) {
        return UUID.nameUUIDFromBytes(n.getBytes());
    }

    static PlaceSummary place(String name, Set<String> interests, double lat, double lon, String quality) {
        return place(name, interests, lat, lon, quality, List.of(), Set.of(), List.of());
    }

    static PlaceSummary place(
            String name,
            Set<String> interests,
            double lat,
            double lon,
            String quality,
            List<PlaceSummary.CostEstimate> costs,
            Set<String> access,
            List<Integer> closed) {
        return new PlaceSummary(
                id(name),
                name,
                lat,
                lon,
                interests.iterator().hasNext() ? interests.iterator().next() : "attraction",
                Set.of(),
                interests,
                "north-coast",
                "North",
                null,
                true,
                60,
                90,
                "LOW",
                costs,
                false,
                quality,
                quality.equals("COMPLETE") ? List.of() : List.of("cost"),
                access,
                closed);
    }

    static final double BASE_LAT = 15.52, BASE_LON = 73.766;

    static List<PlaceSummary> catalogue() {
        List<PlaceSummary> c = new ArrayList<>();
        for (int i = 0; i < 30; i++)
            c.add(place("Food " + i, Set.of("food"), BASE_LAT + i * 0.001, BASE_LON, "COMPLETE"));
        for (int i = 0; i < 5; i++)
            c.add(place("Beach " + i, Set.of("beach"), BASE_LAT + 0.01 + i * 0.002, BASE_LON, "PARTIAL"));
        c.add(place("Old Fort", Set.of("heritage", "viewpoint"), BASE_LAT - 0.03, BASE_LON, "PARTIAL"));
        c.add(place("Far Dive", Set.of("water_sports"), 15.0, 74.0, "PARTIAL"));
        c.add(place(
                "Falls",
                Set.of("nature"),
                15.3,
                74.3,
                "PARTIAL",
                List.of(),
                Set.of("wheelchair:no"),
                List.of(6, 7, 8, 9)));
        c.add(place(
                "Museum",
                Set.of("culture", "heritage"),
                15.50,
                73.91,
                "COMPLETE",
                List.of(new PlaceSummary.CostEstimate("ENTRY", 10, 10, "INR", "PER_PERSON", "VERIFIED")),
                Set.of("wheelchair:yes"),
                List.of()));
        return c;
    }

    static RecommendationRequest.Builder req() {
        return RecommendationRequest.builder()
                .interests(Map.of("food", 3.0, "beach", 3.0))
                .base(BASE_LAT, BASE_LON)
                .seed(7L);
    }

    static List<String> names(RecommendationResult r) {
        return r.selected().stream().map(p -> p.place().name()).toList();
    }

    @Test
    void coversEveryRequestedInterestAndExplainsPicks() {
        RecommendationResult r = RecommendationEngine.recommend(catalogue(), req().build());
        assertEquals(List.of("beach", "food"), r.coverage().covered());
        assertTrue(r.coverage().uncovered().isEmpty());
        RecommendationResult.Pick first = r.selected().getFirst();
        assertFalse(first.newlyCovered().isEmpty());
        assertTrue(first.reason().contains("covers"));
        assertTrue(first.selectionCost() > 0);
    }

    @Test
    void scoreBreakdownIsTransparent() {
        RecommendationResult r = RecommendationEngine.recommend(catalogue(), req().build());
        ScoreBreakdown s = r.selected().getFirst().score();
        double recomputed = ScoreBreakdown.W_INTEREST * s.interest()
                + ScoreBreakdown.W_DISTANCE * s.distance()
                + ScoreBreakdown.W_BUDGET * s.budget()
                + ScoreBreakdown.W_DURATION * s.duration()
                + ScoreBreakdown.W_CONFIDENCE * s.confidence();
        assertEquals(recomputed, s.total(), 1e-9);
        assertNotNull(s.distanceKm());
    }

    @Test
    void changingPreferencesChangesResults() {
        RecommendationResult food = RecommendationEngine.recommend(
                catalogue(), req().interests(Map.of("food", 5.0)).build());
        RecommendationResult heritage = RecommendationEngine.recommend(
                catalogue(), req().interests(Map.of("heritage", 5.0)).build());
        assertNotEquals(names(food), names(heritage));
        assertTrue(names(heritage).stream().noneMatch(n -> n.startsWith("Food")));
    }

    @Test
    void avoidedPlacesAreNeverSelectedOrPooled() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(), req().avoid(Set.of(id("Beach 0"), id("Food 0"))).build());
        assertFalse(names(r).contains("Beach 0"));
        assertTrue(r.fill().stream().noneMatch(s -> s.place().name().equals("Food 0")));
        assertEquals(2, r.excluded().get("avoided"));
    }

    @Test
    void mustVisitIsIncludedEvenWithoutInterestMatch() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(), req().mustVisit(Set.of(id("Museum"))).build());
        assertEquals("Museum", r.selected().getFirst().place().name());
        assertTrue(r.selected().getFirst().mustVisit());
    }

    @Test
    void mustVisitConflictsAreInfeasible() {
        ApiException both = assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(),
                        req().mustVisit(Set.of(id("Museum")))
                                .avoid(Set.of(id("Museum")))
                                .build()));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, both.status());
        assertEquals("INFEASIBLE_PLAN", both.errorCode());
        assertNotNull(both.details().get("conflicts"));
        assertNotNull(both.details().get("suggestedRelaxations"));

        ApiException closed = assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(),
                        req().mustVisit(Set.of(id("Falls"))).travelMonth(7).build()));
        assertEquals("INFEASIBLE_PLAN", closed.errorCode());

        ApiException unknown = assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(), req().mustVisit(Set.of(id("Nope"))).build()));
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status());
    }

    @Test
    void seasonalClosureIsAHardFilterOnlyInClosedMonths() {
        var interests = Map.of("nature", 5.0);
        RecommendationResult july = RecommendationEngine.recommend(
                catalogue(), req().interests(interests).travelMonth(7).build());
        RecommendationResult dec = RecommendationEngine.recommend(
                catalogue(), req().interests(interests).travelMonth(12).build());
        assertEquals(List.of("nature"), july.coverage().uncovered());
        assertEquals(1, july.excluded().get("seasonalClosure"));
        assertEquals(List.of("Falls"), names(dec));
    }

    @Test
    void wheelchairRequirementExcludesKnownInaccessible() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(),
                req().interests(Map.of("nature", 5.0)).requireWheelchair(true).build());
        assertTrue(r.selected().isEmpty());
        assertEquals(1, r.excluded().get("accessibility"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("wheelchair")));
    }

    @Test
    void rareInterestSurvivesPruningAndBeatsRankingOnlyBaseline() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(),
                req().interests(Map.of("food", 5.0, "water_sports", 1.0))
                        .poolSize(5)
                        .build());
        assertTrue(r.pool().reserved().containsKey("water_sports"));
        assertTrue(r.coverage().covered().contains("water_sports"));
        assertTrue(names(r).contains("Far Dive"));
        assertTrue(r.rankingOnlyBaseline().uncovered().contains("water_sports"));
    }

    @Test
    void uncoverableInterestIsReported() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(),
                req().interests(Map.of("food", 2.0, "nightlife", 2.0)).build());
        assertEquals(List.of("nightlife"), r.coverage().uncovered());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("nightlife")));
    }

    @Test
    void sameSeedIsReproducibleAndSelectionDoesNotDependOnSeed() {
        RecommendationResult a = RecommendationEngine.recommend(catalogue(), req().build());
        RecommendationResult b = RecommendationEngine.recommend(catalogue(), req().build());
        RecommendationResult c =
                RecommendationEngine.recommend(catalogue(), req().seed(99L).build());
        assertEquals(a, b);
        assertEquals(names(a), names(c));
        assertEquals(7L, a.seed());
        assertEquals(99L, c.seed());
    }

    @Test
    void lazyHeapMatchesFullGreedyWithFewerEvaluations() {
        RecommendationResult r = RecommendationEngine.recommend(
                catalogue(),
                req().interests(Map.of("food", 2.0, "beach", 3.0, "heritage", 4.0, "culture", 1.0))
                        .poolSize(30)
                        .build());
        assertTrue(r.coverage().lazyEqualsFull());
        assertTrue(r.coverage().gainEvaluations() <= r.coverage().fullGreedyGainEvaluations());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(), req().interests(Map.of("snow", 1.0)).build()));
        assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(), req().interests(Map.of()).build()));
        assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(), req().interests(Map.of("food", 0.0)).build()));
    }

    @Test
    void unknownCostIsFlaggedNotZero() {
        RecommendationResult r = RecommendationEngine.recommend(catalogue(), req().build());
        var beachPick = r.selected().stream()
                .filter(p -> p.place().name().startsWith("Beach"))
                .findFirst()
                .orElseThrow();
        assertTrue(beachPick.score().notes().contains("cost unknown"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("unknown cost")));
    }

    @Test
    void quotaPerInterestIsConfigurableAndFillKeepsTheWholePool() {
        RecommendationResult dflt =
                RecommendationEngine.recommend(catalogue(), req().poolSize(30).build());
        assertEquals(3, dflt.pool().reserved().get("beach"));
        RecommendationResult wide = RecommendationEngine.recommend(
                catalogue(), req().poolSize(30).quotaPerInterest(5).build());
        assertEquals(5, wide.pool().reserved().get("beach"));
        assertEquals(30, wide.selected().size() + wide.fill().size());
        ApiException e = assertThrows(
                ApiException.class,
                () -> RecommendationEngine.recommend(
                        catalogue(), req().quotaPerInterest(51).build()));
        assertEquals("BAD_QUOTA", e.errorCode());
    }
}
