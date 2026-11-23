package com.tripweaver.planning;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/itineraries")
public class ItineraryController {

    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    /**
     * Generates a day-wise itinerary. Re-planning is the same call with {@code keepStops}, {@code fixedStops},
     * {@code mustVisit} and {@code avoid} reflecting the user's edits.
     */
    @PostMapping("/generate")
    public ItineraryService.Response generate(@RequestBody ItineraryRequest req) {
        return service.generate(req);
    }
}
