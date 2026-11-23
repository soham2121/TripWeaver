package com.tripweaver.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripweaver.place.PlaceCatalogue;
import com.tripweaver.support.TestCatalogue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Place discovery API over the real imported Goa catalogue; no external providers or credentials. */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestCatalogue testCatalogue;

    @Autowired
    PlaceCatalogue catalogue;

    @BeforeEach
    void load() {
        testCatalogue.ensureGoaImported();
    }

    @Test
    void searchByWordPrefixUsesTrie() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("q", "aguada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem("Fort Aguada")))
                .andExpect(jsonPath("$.items[*].name", everyItem(containsStringIgnoringCase("aguada"))));
    }

    @Test
    void searchFiltersAndPaginates() throws Exception {
        mvc.perform(get("/api/v1/places/search")
                        .param("interest", "beach")
                        .param("region", "canacona")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].regionSlug", everyItem(is("canacona"))))
                .andExpect(jsonPath("$.items[*].interests", everyItem(hasItem("beach"))))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void autocompleteReturnsPlacesAndFacets() throws Exception {
        mvc.perform(get("/api/v1/places/autocomplete").param("q", "basil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label", hasItem("Basilica of Bom Jesus")));
        mvc.perform(get("/api/v1/places/autocomplete").param("q", "candol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("NEIGHBOURHOOD"));
    }

    @Test
    void nearbyFindsPlacesAroundCandolim() throws Exception {
        mvc.perform(get("/api/v1/places/nearby")
                        .param("lat", "15.5202")
                        .param("lon", "73.7659")
                        .param("radiusKm", "1.5")
                        .param("categories", "beach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[*].distanceKm", everyItem(lessThanOrEqualTo(1.5))));
    }

    @Test
    void detailShowsSourcesAndVerifiedCost() throws Exception {
        UUID museum = UUID.nameUUIDFromBytes("osm:node/4532503789".getBytes());
        mvc.perform(get("/api/v1/places/" + museum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costs[0].confidence").value("VERIFIED"))
                .andExpect(jsonPath("$.costs[0].min").value(10.0))
                .andExpect(jsonPath("$.openingHours[0].raw").value("Mo-Th,Sa,Su 09:00-17:00; Fr off"))
                .andExpect(jsonPath("$.sources[*].sourceType", hasItems("OSM", "OFFICIAL")))
                .andExpect(jsonPath("$.attribution").value(containsString("OpenStreetMap")))
                .andExpect(jsonPath("$.osmUrl").value("https://www.openstreetmap.org/node/4532503789"));
    }

    @Test
    void placeWithUnknownCostHasNoZeroCost() throws Exception {
        UUID baga = UUID.nameUUIDFromBytes("osm:relation/6423947".getBytes());
        mvc.perform(get("/api/v1/places/" + baga))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costs.length()").value(0))
                .andExpect(jsonPath("$.summary.missingFields", hasItem("cost")));
    }

    @Test
    void errorsUseApiErrorFormat() throws Exception {
        mvc.perform(get("/api/v1/places/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/places/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places/search").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_LIMIT"));
        mvc.perform(get("/api/v1/places/search").param("destination", "atlantis"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/places/nearby").param("lat", "95").param("lon", "73"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void destinationsAndCoverage() throws Exception {
        mvc.perform(get("/api/v1/destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("goa"))
                .andExpect(jsonPath("$[0].regions.length()").value(12))
                .andExpect(jsonPath("$[0].activityCount").value(greaterThan(3000)));
        mvc.perform(get("/api/v1/destinations/goa/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.accepted").value(greaterThan(5000)))
                .andExpect(jsonPath("$.report.missingFieldCounts.cost").exists());
    }
}
