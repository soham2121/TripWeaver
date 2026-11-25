package com.tripweaver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.support.TestCatalogue;
import java.util.List;
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

    JsonNode trip(String body) throws Exception {
        String s =
                plan(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(s).get("roadTrip");
    }

    @Test
    void stayAtTheDestinationIsPlannedWithTheTrip() throws Exception {
        JsonNode stay = trip(MUMBAI_TO_CANDOLIM + ",\"stayDays\":2,\"maxDrivingHoursPerDay\":6")
                .get("stay");
        assertEquals(2, stay.get("days").asInt());
        assertEquals("goa", stay.get("destination").asString());
        JsonNode days = stay.get("itinerary").get("days");
        assertEquals(2, days.size());
        assertEquals(stay.get("firstDay").asString(), days.get(0).get("date").asString());
        int stops = 0;
        for (JsonNode d : days)
            for (JsonNode st : d.get("stops")) {
                stops++;
                assertFalse(st.get("place").get("name").asString().isBlank());
            }
        assertTrue(stops > 0, "the stay plans places to visit");
    }

    @Test
    void stayOutsideThePlaceDataIsCountedNotPlanned() throws Exception {
        JsonNode stay = trip("\"origin\":{\"name\":\"Mumbai (CSMT)\",\"lat\":18.9398,\"lon\":72.8355},"
                        + "\"destination\":{\"name\":\"Nashik\",\"lat\":20.0,\"lon\":73.79},"
                        + "\"departureDate\":\"2026-12-14\",\"stayDays\":2")
                .get("stay");
        assertTrue(stay.get("itinerary").isNull());
        assertEquals(
                "No place data near Nashik: the stay is counted, but its days are not planned.",
                stay.get("note").asString());
        assertEquals(2, stay.get("days").asInt());
    }

    @Test
    void badStayDays() throws Exception {
        plan(MUMBAI_TO_CANDOLIM + ",\"stayDays\":15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_STAY_DAYS"));
    }

    @Test
    void noStayByDefault() throws Exception {
        assertTrue(trip(MUMBAI_TO_CANDOLIM + ",\"maxDrivingHoursPerDay\":6")
                .get("stay")
                .isNull());
    }

    static JsonNode line(JsonNode trip, String item) {
        for (JsonNode l : trip.get("budget").get("lines"))
            if (l.get("item").asString().equals(item)) return l;
        return null;
    }

    @Test
    void stayNightsAndArrivalDayFoodAreCountedOnceInTheDriveBudget() throws Exception {
        String costs = ",\"maxDrivingHoursPerDay\":6,\"partySize\":2,\"foodPerPersonPerDay\":500,"
                + "\"accommodationPerNight\":4000";
        JsonNode plain = trip(MUMBAI_TO_CANDOLIM + costs);
        int driveDays = plain.get("days").size();
        assertEquals(500.0 * 2 * driveDays, line(plain, "Food").get("max").asDouble());
        assertNull(line(plain, "Stay at Candolim"));

        JsonNode withStay = trip(MUMBAI_TO_CANDOLIM + costs + ",\"stayDays\":2");
        JsonNode stay = withStay.get("stay");
        assertTrue(
                withStay.get("arrival")
                                .asString()
                                .compareTo(stay.get("firstDay").asString() + "T14:00")
                        <= 0,
                "the demo arrives by 14:00, so the arrival day is stay day 1");
        assertEquals(
                500.0 * 2 * (driveDays - 1), line(withStay, "Food").get("max").asDouble());
        assertEquals(
                stay.get("nights").asInt() * 4000.0,
                line(withStay, "Stay at Candolim").get("max").asDouble());
    }

    @Test
    void wayHomeOffersFourOptions() throws Exception {
        JsonNode r = trip(MUMBAI_TO_CANDOLIM + ",\"stayDays\":2,\"planReturn\":true,\"maxDrivingHoursPerDay\":6,"
                + "\"interests\":{\"heritage\":5,\"beach\":4}");
        JsonNode rt = r.get("returnTrip");
        List<String> kinds = new java.util.ArrayList<>();
        for (JsonNode o : rt.get("options")) kinds.add(o.get("kind").asString());
        assertEquals(List.of("DIRECT", "SAME_WAY", "NEW_STOPS", "DIFFERENT_TOWN"), kinds);
        JsonNode direct = rt.get("options").get(0).get("plan");
        java.time.LocalDate last =
                java.time.LocalDate.parse(r.get("stay").get("lastDay").asString());
        assertEquals(
                last.plusDays(1).toString(),
                direct.get("days").get(0).get("date").asString());
        JsonNode lastDay = direct.get("days").get(direct.get("days").size() - 1);
        JsonNode lastItem = lastDay.get("items").get(lastDay.get("items").size() - 1);
        assertEquals("ARRIVE", lastItem.get("type").asString());
        assertTrue(lastItem.get("name").asString().contains("Mumbai"));
        JsonNode four = rt.get("options").get(3);
        assertFalse(four.get("via").isNull(), four.toString());
        assertFalse(
                four.get("plan").isNull(),
                four.get("via").asString() + ": " + four.get("note").asString());
        boolean through = false;
        for (JsonNode d : four.get("plan").get("days"))
            for (JsonNode it : d.get("items"))
                through |= "WAYPOINT".equals(it.get("type").asString())
                        && four.get("via").asString().equals(it.get("name").asString());
        assertTrue(through, "option 4 passes through its town");
        for (JsonNode o : rt.get("options"))
            if (!o.get("plan").isNull()) {
                assertTrue(o.get("plan").get("stay").isNull());
                assertTrue(o.get("plan").get("returnTrip").isNull());
            }
        assertEquals(1, rt.get("chosen").asInt());
    }

    @Test
    void savedChoiceWithoutAPlanFallsBack() throws Exception {
        JsonNode r = trip("\"origin\":{\"name\":\"Panaji\",\"lat\":15.4989,\"lon\":73.8278},"
                + "\"destination\":{\"name\":\"Candolim\",\"lat\":15.5202,\"lon\":73.7659},"
                + "\"departureDate\":\"2026-12-14\",\"planReturn\":true,\"returnChoice\":4");
        JsonNode rt = r.get("returnTrip");
        assertTrue(rt.get("options").get(3).get("plan").isNull());
        assertEquals(1, rt.get("chosen").asInt());
        boolean warned = false;
        for (JsonNode w : r.get("warnings")) warned |= w.asString().contains("could not be planned");
        assertTrue(warned);
    }

    @Test
    void noReturnByDefault() throws Exception {
        assertTrue(trip(MUMBAI_TO_CANDOLIM + ",\"maxDrivingHoursPerDay\":6")
                .get("returnTrip")
                .isNull());
    }
}
