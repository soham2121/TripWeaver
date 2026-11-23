package com.tripweaver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.support.TestCatalogue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Mumbai → Goa on the real Goa and Konkan corridor catalogues (tests use the straight-line estimate). */
@SpringBootTest
@AutoConfigureMockMvc
class RoadTripApiTest {

    static final String MUMBAI_TO_CANDOLIM = "\"origin\":{\"name\":\"Mumbai (CSMT)\",\"lat\":18.9398,\"lon\":72.8355},"
            + "\"destination\":{\"name\":\"Candolim\",\"lat\":15.5202,\"lon\":73.7659},"
            + "\"departureDate\":\"2026-12-14\",\"departureTime\":\"07:00\"";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestCatalogue testCatalogue;

    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void load() {
        testCatalogue.ensureImported("goa");
        testCatalogue.ensureImported("konkan");
    }

    ResultActions plan(String body) throws Exception {
        return mvc.perform(post("/api/v1/road-trips/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + body + "}"));
    }

    @Test
    void mumbaiToGoaDemoWithOvernightAndDetours() throws Exception {
        String s = plan(MUMBAI_TO_CANDOLIM
                        + ",\"maxDrivingHoursPerDay\":6,\"interests\":{\"heritage\":5,\"beach\":4,\"nature\":3},"
                        + "\"partySize\":2,\"mileageKmPerLitre\":15,\"fuelPricePerLitre\":101,\"seed\":2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode r = json.readTree(s).get("roadTrip");
        assertTrue(r.get("days").size() >= 2);
        assertTrue(r.get("nights").asInt() >= 1);
        JsonNode o = r.get("days").get(0).get("overnight");
        assertEquals("SUGGESTED", o.get("kind").asString());
        assertTrue(o.get("lat").asDouble() > 15.8, "overnight lies on the Konkan corridor, north of Goa");
        assertTrue(r.get("detourCandidates").asInt() > 0);
        int detours = 0;
        for (JsonNode seg : r.get("segments"))
            for (JsonNode d : seg.get("detours")) {
                detours++;
                assertTrue(d.get("addedDriveMinutes").asInt() <= 45);
            }
        assertTrue(detours > 0 && detours <= 3);
        for (JsonNode d : r.get("days")) {
            assertTrue(d.get("drivingMinutes").asInt() <= 360);
            assertTrue(d.get("longestContinuousDrivingMinutes").asInt() <= 120);
            assertFalse(d.get("navigationUrls").isEmpty());
        }
        assertEquals("ESTIMATE", r.get("travelProvider").asString());
        assertTrue(r.get("disclaimer").asString().contains("heuristic"));
        boolean tollsUnknown = false;
        for (JsonNode l : r.get("budget").get("lines"))
            if (l.get("category").asString().equals("TOLLS"))
                tollsUnknown = l.get("max").isNull();
        assertTrue(tollsUnknown);
    }

    @Test
    void roadTripErrorsAreExplained() throws Exception {
        plan(MUMBAI_TO_CANDOLIM + ",\"arriveBy\":\"2026-12-14T09:00\"")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INFEASIBLE_PLAN"))
                .andExpect(jsonPath("$.details.estimatedArrival").exists());
        plan(MUMBAI_TO_CANDOLIM + ",\"transport\":\"WALK\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_TRANSPORT"));
    }
}
