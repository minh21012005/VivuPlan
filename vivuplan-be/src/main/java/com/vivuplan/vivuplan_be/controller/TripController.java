package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /** Generate + Save a new AI itinerary */
    @PostMapping("/generate")
    public ResponseEntity<TripDto.TripResponse> generate(
            @Valid @RequestBody TripDto.GenerateRequest req,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(tripService.generateAndSave(userId, req));
    }

    /** Get all trips for current user */
    @GetMapping
    public ResponseEntity<List<TripDto.TripResponse>> myTrips(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(tripService.getUserTrips(userId));
    }

    /** Get single trip by ID */
    @GetMapping("/{id}")
    public ResponseEntity<TripDto.TripResponse> getTrip(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return ResponseEntity.ok(tripService.getTrip(id, userId));
    }

    /** Delete a trip */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTrip(
            @PathVariable Long id,
            Authentication auth) {
        tripService.deleteTrip(id, (Long) auth.getPrincipal());
        return ResponseEntity.ok(Map.of("message", "Đã xóa lịch trình"));
    }

    /** Toggle public/private */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<TripDto.TripResponse> toggleVisibility(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(tripService.togglePublic(id, (Long) auth.getPrincipal()));
    }

    /** Update status (DRAFT, PLANNED, COMPLETED) */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TripDto.TripResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(tripService.updateTripStatus(id, (Long) auth.getPrincipal(), body.get("status")));
    }

    /** Add a custom activity to a day */
    @PostMapping("/{id}/days/{dayNumber}/activities")
    public ResponseEntity<TripDto.TripResponse> addActivity(
            @PathVariable Long id,
            @PathVariable Integer dayNumber,
            @RequestBody TripDto.UpdateActivityRequest req,
            Authentication auth) {
        return ResponseEntity.ok(tripService.addActivity(id, (Long) auth.getPrincipal(), dayNumber, req));
    }

    /** Update one activity */
    @PatchMapping("/{id}/activities/{activityId}")
    public ResponseEntity<TripDto.TripResponse> updateActivity(
            @PathVariable Long id,
            @PathVariable Long activityId,
            @RequestBody TripDto.UpdateActivityRequest req,
            Authentication auth) {
        return ResponseEntity.ok(tripService.updateActivity(id, (Long) auth.getPrincipal(), activityId, req));
    }

    /** Delete one activity */
    @DeleteMapping("/{id}/activities/{activityId}")
    public ResponseEntity<TripDto.TripResponse> deleteActivity(
            @PathVariable Long id,
            @PathVariable Long activityId,
            Authentication auth) {
        return ResponseEntity.ok(tripService.deleteActivity(id, (Long) auth.getPrincipal(), activityId));
    }

    /** Preview an AI-regenerated version of one itinerary day without saving it */
    @PostMapping("/{id}/days/{dayNumber}/regenerate-preview")
    public ResponseEntity<TripDto.RegenerateDayPreviewResponse> previewRegenerateDay(
            @PathVariable Long id,
            @PathVariable Integer dayNumber,
            @RequestBody TripDto.RegenerateDayRequest req,
            Authentication auth) {
        return ResponseEntity.ok(tripService.previewRegenerateDay(id, (Long) auth.getPrincipal(), dayNumber, req));
    }

    /** Apply a previously previewed regenerated day */
    @PostMapping("/{id}/days/{dayNumber}/regenerate-apply")
    public ResponseEntity<TripDto.TripResponse> applyRegenerateDay(
            @PathVariable Long id,
            @PathVariable Integer dayNumber,
            @Valid @RequestBody TripDto.ApplyRegenerateDayRequest req,
            Authentication auth) {
        return ResponseEntity.ok(tripService.applyRegeneratedDay(id, (Long) auth.getPrincipal(), dayNumber, req));
    }

    /** Public trips feed */
    @GetMapping("/public")
    public ResponseEntity<Page<TripDto.TripResponse>> publicTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(tripService.getPublicTrips(page, size));
    }

    /** Get trip by share code */
    @GetMapping("/public/share/{code}")
    public ResponseEntity<TripDto.TripResponse> getByShareCode(@PathVariable String code) {
        return ResponseEntity.ok(tripService.getByShareCode(code));
    }
}
