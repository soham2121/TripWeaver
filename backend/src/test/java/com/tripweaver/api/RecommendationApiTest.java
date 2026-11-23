package com.tripweaver.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.support.TestCatalogue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Recommendation pipeline on the real Goa catalogue. */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationApiTest {

    static final String CANDOLIM = "\"baseLat\":15.5202,\"baseLon\":73.7659";
    static final UUID DUDHSAGAR = UUID.nameUUIDFromBytes("osm:node/1241285499".getBytes());
    static final UUID BASILICA = UUID.nameUUIDFromBytes("osm:relation/3569663".getBytes());

    @Autowired
    MockMvc mvc;

    @Autowired
    TestCatalogue testCatalogue;

    @BeforeEach
    void load() {
        testCatalogue.ensureGoaImported();
    }

    MvcResult preview(String body) throws Exception {
        return mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void goaDemoPreferencesAreCoveredWithExplanations() throws Exception {
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"beach\":5,\"food\":4,\"heritage\":4,\"adventure\":2}," + CANDOLIM
                                + ",\"seed\":2026}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.coverage.uncovered.length()").value(0))
                .andExpect(jsonPath("$.result.coverage.lazyEqualsFull").value(true))
                .andExpect(jsonPath("$.result.seed").value(2026))
                .andExpect(jsonPath("$.result.selected[0].reason").value(containsString("covers")))
                .andExpect(jsonPath("$.result.selected[0].score.total").isNumber())
                .andExpect(jsonPath("$.runId").exists());
    }

    @Test
    void preferencesChangeResults() throws Exception {
        String a = preview("{\"interests\":{\"nightlife\":5}," + CANDOLIM + "}")
                .getResponse()
                .getContentAsString();
        String b = preview("{\"interests\":{\"wildlife\":5}," + CANDOLIM + "}")
                .getResponse()
                .getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(
                a.replaceAll("\"runId\":\"[^\"]+\",\"durationMicros\":\\d+", ""),
                b.replaceAll("\"runId\":\"[^\"]+\",\"durationMicros\":\\d+", ""));
    }

    @Test
    void rareWaterSportsInterestIsPreservedNextToFood() throws Exception {
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"food\":5,\"water_sports\":1}," + CANDOLIM + ",\"poolSize\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.coverage.covered", hasItem("water_sports")))
                .andExpect(jsonPath("$.result.rankingOnlyBaseline.uncovered", hasItem("water_sports")));
    }

    @Test
    void monsoonClosureAndMustVisitConflictIsInfeasible() throws Exception {
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"nature\":5},\"travelMonth\":7,\"mustVisit\":[\"" + DUDHSAGAR
                                + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INFEASIBLE_PLAN"))
                .andExpect(jsonPath("$.details.conflicts[0]").value(containsString("Dudhsagar")));
    }

    @Test
    void mustVisitAndAvoidAreHonoured() throws Exception {
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"heritage\":5,\"spiritual\":3},\"mustVisit\":[\"" + BASILICA + "\"],"
                                + CANDOLIM + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.selected[0].place.name").value("Basilica of Bom Jesus"))
                .andExpect(jsonPath("$.result.selected[0].mustVisit").value(true));
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"heritage\":5,\"spiritual\":5},\"avoid\":[\"" + BASILICA + "\"],"
                                + CANDOLIM + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.selected[*].place.name", not(hasItem("Basilica of Bom Jesus"))))
                .andExpect(jsonPath("$.result.fill[*].place.name", not(hasItem("Basilica of Bom Jesus"))));
    }

    @Test
    void invalidInterestIsBadRequestAndMetadataIsLogged() throws Exception {
        mvc.perform(post("/api/v1/recommendations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":{\"skiing\":3}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_INTEREST"));
        preview("{\"interests\":{\"beach\":3},\"seed\":5}");
        mvc.perform(get("/api/v1/algorithms/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithms.length()").value(5))
                .andExpect(jsonPath(
                        "$.recentRuns[*].algorithm",
                        hasItems(
                                "randomized-quickselect+category-quotas",
                                "lazy-greedy-weighted-set-cover+binary-heap")));
    }
}
