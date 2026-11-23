package com.tripweaver.api;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.support.TestCatalogue;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Multi-day itinerary generation on the real Goa catalogue. */
@SpringBootTest
@AutoConfigureMockMvc
class ItineraryApiTest {

    static final String CANDOLIM = "\"baseLat\":15.5202,\"baseLon\":73.7659";
    static final UUID DUDHSAGAR = UUID.nameUUIDFromBytes("osm:node/1241285499".getBytes());
    static final UUID ARCH_MUSEUM = UUID.nameUUIDFromBytes("osm:node/4532503789".getBytes());
    static final String DEMO = "{" + CANDOLIM + ",\"startDate\":\"2026-12-14\",\"days\":3,\"partySize\":2,"
            + "\"interests\":{\"beach\":5,\"food\":4,\"heritage\":4,\"adventure\":2},\"seed\":2026,"
            + "\"budget\":{\"amount\":30000,\"basis\":\"TOTAL\",\"strictness\":\"FLEXIBLE\"},"
            + "\"costs\":{\"foodPerPersonPerDay\":1200,\"accommodationPerNight\":4000,"
            + "\"fuelPricePerLitre\":101.0,\"mileageKmPerLitre\":15}";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestCatalogue testCatalogue;

    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void load() {
        testCatalogue.ensureGoaImported();
    }

    ResultActions generate(String body) throws Exception {
        return mvc.perform(post("/api/v1/itineraries/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    JsonNode ok(String body) throws Exception {
        String s = generate(body)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(s).get("itinerary");
    }

    static String id(JsonNode stop) {
        return stop.get("place").get("id").asString();
    }

    @Test
    void goaDemoTripIsFeasibleDayByDay() throws Exception {
        JsonNode it = ok(DEMO + "}");
        assertEquals(3, it.get("days").size());
        assertEquals(
                0,
                it.get("coverage").get("uncovered").size(),
                it.get("coverage").toString());
        for (JsonNode d : it.get("days")) {
            assertTrue(d.get("stops").size() > 0);
            LocalTime end = LocalTime.parse(d.get("end").asString());
            LocalTime prev = LocalTime.parse(d.get("start").asString());
            for (JsonNode s : d.get("stops")) {
                LocalTime a = LocalTime.parse(s.get("arrive").asString());
                LocalTime st = LocalTime.parse(s.get("start").asString());
                LocalTime e = LocalTime.parse(s.get("end").asString());
                assertFalse(a.isBefore(prev));
                assertFalse(st.isBefore(a));
                assertFalse(e.isAfter(end));
                prev = e;
            }
            assertFalse(LocalTime.parse(d.get("endOfDay").asString()).isAfter(end));
            assertTrue(d.get("workload").get("utilisation").asDouble() <= 0.70 + 1e-9);
        }
        JsonNode budget = it.get("budget");
        assertEquals("INR", budget.get("currency").asString());
        assertTrue(budget.get("knownMax").asDouble() >= 1200 * 2 * 3 + 4000 * 2);
        assertTrue(it.get("travelBasis").asString().contains("Estimate"));
        assertTrue(it.get("disclaimer").asString().contains("not guarantee"));
    }

    @Test
    void replanningKeepsStopsAndHonoursNewAvoid() throws Exception {
        JsonNode day1 = ok(DEMO + "}").get("days").get(0).get("stops");
        String dropped = id(day1.get(0));
        List<String> keep = new ArrayList<>();
        for (int i = 1; i < day1.size(); i++) keep.add("{\"placeId\":\"" + id(day1.get(i)) + "\",\"day\":1}");
        JsonNode second = ok(DEMO + ",\"avoid\":[\"" + dropped + "\"],\"keepStops\":[" + String.join(",", keep) + "]}");
        List<String> newDay1 = new ArrayList<>();
        second.get("days").get(0).get("stops").forEach(s -> newDay1.add(id(s)));
        for (int i = 1; i < day1.size(); i++) assertTrue(newDay1.contains(id(day1.get(i))), "kept stop moved");
        second.get("days").forEach(d -> d.get("stops").forEach(s -> assertNotEquals(dropped, id(s))));
    }

    @Test
    void verifiedFridayClosureAndMonsoonClosureAreHardConstraints() throws Exception {
        // 2026-12-18 is a Friday; the Archaeological Museum is closed on Fridays (verified source).
        generate("{" + CANDOLIM + ",\"startDate\":\"2026-12-18\",\"interests\":{\"heritage\":5}," + "\"mustVisit\":[\""
                        + ARCH_MUSEUM + "\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INFEASIBLE_PLAN"))
                .andExpect(jsonPath("$.details.conflicts[0]").value(containsString("opening hours")));
        JsonNode twoDays = ok("{" + CANDOLIM + ",\"startDate\":\"2026-12-18\",\"days\":2,"
                + "\"interests\":{\"heritage\":5},\"mustVisit\":[\"" + ARCH_MUSEUM + "\"]}");
        boolean onSaturday = false;
        for (JsonNode s : twoDays.get("days").get(1).get("stops"))
            if (id(s).equals(ARCH_MUSEUM.toString())) {
                onSaturday = true;
                assertEquals("CHECKED", s.get("hours").get("status").asString());
                assertEquals("VERIFIED", s.get("hours").get("confidence").asString());
            }
        assertTrue(onSaturday);

        generate("{" + CANDOLIM + ",\"startDate\":\"2026-07-10\",\"interests\":{\"nature\":5}," + "\"mustVisit\":[\""
                        + DUDHSAGAR + "\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INFEASIBLE_PLAN"));
    }

    @Test
    void hardBudgetBelowMandatoryCostsIsRejectedWithFigures() throws Exception {
        generate("{" + CANDOLIM + ",\"startDate\":\"2026-12-14\",\"partySize\":2,\"interests\":{\"heritage\":5},"
                        + "\"mustVisit\":[\"" + ARCH_MUSEUM + "\"],"
                        + "\"budget\":{\"amount\":5,\"basis\":\"PER_PERSON\",\"strictness\":\"HARD\"}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUDGET_INFEASIBLE"))
                .andExpect(jsonPath("$.details.estimatedMaximum").value(20.0))
                .andExpect(jsonPath("$.details.budget").value(10.0));
    }

    @Test
    void invalidInputIsBadRequest() throws Exception {
        generate("{" + CANDOLIM + ",\"days\":30,\"interests\":{\"beach\":5}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_DAYS"));
        generate("{\"interests\":{\"beach\":5}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_BASE"));
        generate("{" + CANDOLIM + ",\"pace\":\"SPEEDY\",\"interests\":{\"beach\":5}}")
                .andExpect(status().isBadRequest());
    }
}
