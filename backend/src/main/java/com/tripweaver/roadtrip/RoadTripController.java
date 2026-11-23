package com.tripweaver.roadtrip;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/road-trips")
public class RoadTripController {

    private final RoadTripService service;

    public RoadTripController(RoadTripService service) {
        this.service = service;
    }

    /** Plans a road trip with fixed waypoints, optional detours and driving days. Stateless. */
    @PostMapping("/plan")
    public RoadTripService.Response plan(@RequestBody RoadTripRequest req) {
        return service.plan(req);
    }
}
