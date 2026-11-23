package com.tripweaver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.support.TestCatalogue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Owner / editor / viewer access, share links, optimistic locking and collaboration (plan section 8). */
@SpringBootTest
@AutoConfigureMockMvc
class TripCollaborationApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestCatalogue testCatalogue;

    final JsonMapper json = JsonMapper.builder().build();

    record User(String username, String id, MockHttpSession session) {}

    User user(String prefix) throws Exception {
        String u = prefix + UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession s = new MockHttpSession();
        String body = mvc.perform(post("/api/v1/auth/register")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"goa-trip-2026\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new User(u, json.readTree(body).get("id").asString(), s);
    }

    ResultActions call(MockHttpServletRequestBuilder b, User u, String body) throws Exception {
        if (u != null) b.session(u.session());
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b);
    }

    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    String createTrip(User owner) throws Exception {
        return read(call(
                                post("/api/v1/trips"),
                                owner,
                                "{\"title\":\"Goa with friends\",\"kind\":\"ITINERARY\","
                                        + "\"request\":{\"baseLat\":15.52,\"baseLon\":73.77,\"interests\":{\"beach\":5}},"
                                        + "\"result\":{\"days\":[]}}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.version").value(0))
                        .andExpect(jsonPath("$.role").value("OWNER")))
                .get("id")
                .asString();
    }

    void addMember(User owner, String trip, User member, String role) throws Exception {
        call(
                        post("/api/v1/trips/" + trip + "/members"),
                        owner,
                        "{\"username\":\"" + member.username() + "\",\"role\":\"" + role + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void ownerEditorAndViewerHaveDifferentPermissions() throws Exception {
        User owner = user("own"), editor = user("edi"), viewer = user("vie"), outsider = user("out");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;

        call(get(t), outsider, null).andExpect(status().isNotFound());
        addMember(owner, trip, viewer, "VIEWER");
        addMember(owner, trip, editor, "EDITOR");

        call(get(t), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
        call(get("/api/v1/trips"), viewer, null)
                .andExpect(jsonPath("$[?(@.id == '" + trip + "')].role").value("VIEWER"));
        call(patch(t), viewer, "{\"version\":0,\"result\":{\"days\":[1]}}").andExpect(status().isForbidden());

        call(patch(t), editor, "{\"version\":0,\"result\":{\"days\":[1]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        call(patch(t), editor, "{\"version\":1,\"title\":\"Mine now\"}").andExpect(status().isForbidden());
        call(post(t + "/members"), editor, "{\"username\":\"" + outsider.username() + "\",\"role\":\"VIEWER\"}")
                .andExpect(status().isForbidden());
        call(post(t + "/share-links"), editor, "{}").andExpect(status().isForbidden());
        call(delete(t), editor, null).andExpect(status().isForbidden());

        call(patch(t + "/members/" + viewer.id()), owner, "{\"role\":\"EDITOR\"}")
                .andExpect(status().isOk());
        call(get(t), viewer, null).andExpect(jsonPath("$.role").value("EDITOR"));
        call(delete(t + "/members/" + viewer.id()), owner, null).andExpect(status().isNoContent());
        call(get(t), viewer, null).andExpect(status().isNotFound());

        call(patch(t), owner, "{\"version\":1,\"title\":\"Goa 2026\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Goa 2026"));
        call(get(t), owner, null).andExpect(jsonPath("$.members.length()").value(1));
        call(delete(t), owner, null).andExpect(status().isNoContent());
        call(get(t), owner, null).andExpect(status().isNotFound());
    }

    @Test
    void memberRulesAreValidated() throws Exception {
        User owner = user("own");
        String t = "/api/v1/trips/" + createTrip(owner);
        call(post(t + "/members"), owner, "{\"username\":\"no-such-user-xyz\",\"role\":\"VIEWER\"}")
                .andExpect(status().isNotFound());
        call(post(t + "/members"), owner, "{\"username\":\"" + owner.username() + "\",\"role\":\"VIEWER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OWNER_IS_MEMBER"));
        User v = user("vie");
        call(post(t + "/members"), owner, "{\"username\":\"" + v.username() + "\",\"role\":\"OWNER\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void shareLinkIsReadOnlyUnguessableAndRevocable() throws Exception {
        User owner = user("own");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;
        JsonNode link = read(call(post(t + "/share-links"), owner, "{}").andExpect(status().isCreated()));
        String token = link.get("token").asString();
        assertTrue(token.length() >= 43, "256-bit token");
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM trip_share_links WHERE token_hash = ?", Integer.class, token),
                "the token itself is never stored");

        call(get("/api/v1/shared/" + token), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Goa with friends"))
                .andExpect(jsonPath("$.result.days").isArray())
                .andExpect(jsonPath("$.members").doesNotExist())
                .andExpect(jsonPath("$.request").doesNotExist());
        call(patch(t), null, "{\"version\":0,\"title\":\"x\"}").andExpect(status().isUnauthorized());
        call(get(t + "/share-links"), owner, null)
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].token").doesNotExist());

        call(delete(t + "/share-links/" + link.get("id").asString()), owner, null)
                .andExpect(status().isNoContent());
        call(get("/api/v1/shared/" + token), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHARE_LINK_INVALID"));

        JsonNode expiring = read(call(post(t + "/share-links"), owner, "{\"expiresInHours\":24}")
                .andExpect(jsonPath("$.expiresAt").exists()));
        String token2 = expiring.get("token").asString();
        call(get("/api/v1/shared/" + token2), null, null).andExpect(status().isOk());
        jdbc.update(
                "UPDATE trip_share_links SET expires_at = expires_at - INTERVAL '2' DAY WHERE id = ?",
                UUID.fromString(expiring.get("id").asString()));
        call(get("/api/v1/shared/" + token2), null, null).andExpect(status().isNotFound());
        call(get("/api/v1/shared/not-a-real-token"), null, null).andExpect(status().isNotFound());
    }

    @Test
    void staleEditsReturnAConflict() throws Exception {
        User owner = user("own"), editor = user("edi");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;
        addMember(owner, trip, editor, "EDITOR");
        call(patch(t), owner, "{\"version\":0,\"result\":{\"days\":[\"owner\"]}}")
                .andExpect(status().isOk());
        call(patch(t), editor, "{\"version\":0,\"result\":{\"days\":[\"editor\"]}}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.details.currentVersion").value(1));
        call(get(t), editor, null).andExpect(jsonPath("$.result.days[0]").value("owner"));
        call(patch(t), editor, "{\"result\":{\"days\":[]}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERSION_REQUIRED"));
    }

    @Test
    void suggestionsAndOneVotePerPerson() throws Exception {
        testCatalogue.ensureImported("goa");
        String place = jdbc.queryForObject(
                        "SELECT p.id FROM places p JOIN destinations d ON d.id = p.destination_id"
                                + " WHERE d.slug = 'goa' AND p.is_activity ORDER BY p.name LIMIT 1",
                        UUID.class)
                .toString();
        User owner = user("own"), editor = user("edi"), viewer = user("vie");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;
        addMember(owner, trip, editor, "EDITOR");
        addMember(owner, trip, viewer, "VIEWER");

        call(post(t + "/suggestions"), viewer, "{\"placeId\":\"" + place + "\"}")
                .andExpect(status().isForbidden());
        call(post(t + "/suggestions"), editor, "{\"placeId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());
        String sid = read(call(
                                post(t + "/suggestions"),
                                editor,
                                "{\"placeId\":\"" + place + "\",\"day\":1,\"note\":\"Sunset\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.placeName").exists()))
                .get("id")
                .asString();
        String votes = t + "/suggestions/" + sid + "/votes";
        call(post(votes), editor, "{\"value\":1}").andExpect(status().isCreated());
        call(post(votes), editor, "{\"value\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_VOTE"));
        call(post(votes), editor, "{\"value\":-1}").andExpect(status().isOk());
        call(post(votes), owner, "{\"value\":1}").andExpect(status().isCreated());
        call(post(votes), viewer, "{\"value\":1}").andExpect(status().isForbidden());
        call(post(votes), owner, "{\"value\":5}").andExpect(status().isBadRequest());

        call(get(t + "/suggestions"), viewer, null)
                .andExpect(jsonPath("$[0].up").value(1))
                .andExpect(jsonPath("$[0].down").value(1))
                .andExpect(jsonPath("$[0].note").value("Sunset"))
                .andExpect(jsonPath("$[0].myVote").value(0));
        call(get(t + "/suggestions"), editor, null)
                .andExpect(jsonPath("$[0].myVote").value(-1));
    }

    @Test
    void changeHistoryRecordsWhoDidWhat() throws Exception {
        User owner = user("own"), editor = user("edi");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;
        addMember(owner, trip, editor, "EDITOR");
        call(patch(t), editor, "{\"version\":0,\"result\":{\"days\":[]}}").andExpect(status().isOk());
        String link =
                read(call(post(t + "/share-links"), owner, "{}")).get("id").asString();
        call(delete(t + "/share-links/" + link), owner, null).andExpect(status().isNoContent());

        JsonNode h = read(call(get(t + "/history"), editor, null).andExpect(status().isOk()));
        List<String> actions = new ArrayList<>();
        h.forEach(e -> actions.add(e.get("action").asString()));
        assertEquals(
                List.of("SHARE_REVOKED", "SHARE_CREATED", "ITINERARY_UPDATED", "MEMBER_ADDED", "CREATED"), actions);
        assertEquals(editor.username(), h.get(2).get("username").asString());
        assertEquals(1, h.get(2).get("version").asInt());
    }

    @Test
    void sharedExpensesSplitEquallyAmongParticipants() throws Exception {
        User owner = user("own"), editor = user("edi"), viewer = user("vie");
        String trip = createTrip(owner);
        String t = "/api/v1/trips/" + trip;
        addMember(owner, trip, editor, "EDITOR");
        addMember(owner, trip, viewer, "VIEWER");
        call(post(t + "/expenses"), owner, "{\"description\":\"Scooter hire\",\"amount\":3000}")
                .andExpect(status().isCreated());
        call(post(t + "/expenses"), editor, "{\"description\":\"Lunch\",\"amount\":600}")
                .andExpect(status().isCreated());
        call(post(t + "/expenses"), viewer, "{\"description\":\"Taxi\",\"amount\":100}")
                .andExpect(status().isForbidden());
        call(post(t + "/expenses"), owner, "{\"description\":\"Bad\",\"amount\":-5}")
                .andExpect(status().isBadRequest());

        JsonNode e = read(call(get(t + "/expenses"), viewer, null).andExpect(status().isOk()));
        assertEquals(3600, e.get("total").asDouble(), 0.001);
        assertEquals(1200, e.get("perPerson").asDouble(), 0.001);
        assertEquals(2, e.get("items").size());
        assertEquals(
                "Added expense \"Lunch\" (₹600)",
                read(call(get(t + "/history"), owner, null))
                        .get(0)
                        .get("summary")
                        .asString());
        for (JsonNode b : e.get("balances")) {
            double expected = b.get("username").asString().equals(owner.username())
                    ? 1800
                    : b.get("username").asString().equals(editor.username()) ? -600 : -1200;
            assertEquals(expected, b.get("balance").asDouble(), 0.001, b.toString());
        }
    }

    @Test
    void generateRunsThePlannerAndSavesTheResultWithVersionCheck() throws Exception {
        testCatalogue.ensureImported("goa");
        User owner = user("own");
        String trip = read(call(
                                post("/api/v1/trips"),
                                owner,
                                "{\"title\":\"Beach day\",\"kind\":\"ITINERARY\",\"request\":{\"destination\":\"goa\","
                                        + "\"baseLat\":15.5202,\"baseLon\":73.7659,\"days\":1,\"startDate\":\"2026-12-14\","
                                        + "\"interests\":{\"beach\":5}}}")
                        .andExpect(status().isCreated()))
                .get("id")
                .asString();
        String t = "/api/v1/trips/" + trip;
        call(post(t + "/generate"), owner, "{\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.result.days.length()").value(1))
                .andExpect(jsonPath("$.result.days[0].stops").isNotEmpty());
        call(post(t + "/generate"), owner, "{\"version\":0}").andExpect(status().isConflict());
        call(
                        post(t + "/generate"),
                        owner,
                        "{\"version\":1,\"request\":{\"destination\":\"goa\",\"baseLat\":15.5202,\"baseLon\":73.7659,"
                                + "\"days\":2,\"startDate\":\"2026-12-14\",\"interests\":{\"beach\":5}}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.days").value(2))
                .andExpect(jsonPath("$.result.days.length()").value(2));
    }

    @Test
    void publicShareEndpointIsRateLimited() throws Exception {
        int last = 0;
        for (int i = 0; i < 70; i++)
            last = mvc.perform(get("/api/v1/shared/unknown-token").with(r -> {
                        r.setRemoteAddr("10.9.9.9");
                        return r;
                    }))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        assertEquals(429, last);
        mvc.perform(get("/api/v1/shared/unknown-token").with(r -> {
                    r.setRemoteAddr("10.9.9.10");
                    return r;
                }))
                .andExpect(status().isNotFound());
    }
}
