package com.tripweaver.trip;

import com.tripweaver.auth.AppUser;
import com.tripweaver.trip.TripService.Kind;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class TripController {

    public record CreateTrip(String title, Kind kind, JsonNode request, JsonNode result) {}

    public record UpdateTrip(Integer version, String title, JsonNode request, JsonNode result) {}

    public record Generate(Integer version, JsonNode request) {}

    public record AddMember(String username, String role) {}

    public record ChangeRole(String role) {}

    public record NewShareLink(Integer expiresInHours) {}

    public record NewSuggestion(UUID placeId, Integer day, String note) {}

    public record Vote(Integer value) {}

    public record NewExpense(String description, Double amount, UUID paidBy) {}

    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    @PostMapping("/trips")
    public ResponseEntity<TripService.TripView> create(@RequestBody CreateTrip b) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trips.create(AppUser.current(), b.title(), b.kind(), b.request(), b.result()));
    }

    @GetMapping("/trips")
    public List<TripService.TripSummary> list() {
        return trips.list(AppUser.current());
    }

    @GetMapping("/trips/{id}")
    public TripService.TripView get(@PathVariable UUID id) {
        return trips.get(AppUser.current(), id);
    }

    @PatchMapping("/trips/{id}")
    public TripService.TripView update(@PathVariable UUID id, @RequestBody UpdateTrip b) {
        return trips.update(AppUser.current(), id, b.version(), b.title(), b.request(), b.result());
    }

    @PostMapping("/trips/{id}/generate")
    public TripService.TripView generate(@PathVariable UUID id, @RequestBody Generate b) {
        return trips.generate(AppUser.current(), id, b.version(), b.request());
    }

    @DeleteMapping("/trips/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        trips.archive(AppUser.current(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trips/{id}/members")
    public ResponseEntity<TripService.Member> addMember(@PathVariable UUID id, @RequestBody AddMember b) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trips.addMember(AppUser.current(), id, b.username(), b.role()));
    }

    @PatchMapping("/trips/{id}/members/{userId}")
    public ResponseEntity<Void> changeRole(
            @PathVariable UUID id, @PathVariable UUID userId, @RequestBody ChangeRole b) {
        trips.changeRole(AppUser.current(), id, userId, b.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/trips/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        trips.removeMember(AppUser.current(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trips/{id}/share-links")
    public ResponseEntity<TripService.ShareLink> share(
            @PathVariable UUID id, @RequestBody(required = false) NewShareLink b) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trips.createShareLink(AppUser.current(), id, b == null ? null : b.expiresInHours()));
    }

    @GetMapping("/trips/{id}/share-links")
    public List<TripService.ShareLink> shareLinks(@PathVariable UUID id) {
        return trips.shareLinks(AppUser.current(), id);
    }

    @DeleteMapping("/trips/{id}/share-links/{linkId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID linkId) {
        trips.revokeShareLink(AppUser.current(), id, linkId);
        return ResponseEntity.noContent().build();
    }

    /** Public read-only view of a shared trip; no sign-in. */
    @GetMapping("/shared/{token}")
    public TripService.SharedTrip shared(@PathVariable String token) {
        return trips.shared(token);
    }

    @PostMapping("/trips/{id}/suggestions")
    public ResponseEntity<TripService.Suggestion> suggest(@PathVariable UUID id, @RequestBody NewSuggestion b) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trips.suggest(AppUser.current(), id, b.placeId(), b.day(), b.note()));
    }

    @GetMapping("/trips/{id}/suggestions")
    public List<TripService.Suggestion> suggestions(@PathVariable UUID id) {
        return trips.suggestions(AppUser.current(), id);
    }

    @PostMapping("/trips/{id}/suggestions/{sid}/votes")
    public ResponseEntity<Void> vote(@PathVariable UUID id, @PathVariable UUID sid, @RequestBody Vote b) {
        boolean created = trips.vote(AppUser.current(), id, sid, b.value());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .build();
    }

    @GetMapping("/trips/{id}/history")
    public List<TripService.History> history(@PathVariable UUID id) {
        return trips.history(AppUser.current(), id);
    }

    @PostMapping("/trips/{id}/expenses")
    public ResponseEntity<TripService.Expense> addExpense(@PathVariable UUID id, @RequestBody NewExpense b) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trips.addExpense(AppUser.current(), id, b.description(), b.amount(), b.paidBy()));
    }

    @GetMapping("/trips/{id}/expenses")
    public TripService.Expenses expenses(@PathVariable UUID id) {
        return trips.expenses(AppUser.current(), id);
    }
}
