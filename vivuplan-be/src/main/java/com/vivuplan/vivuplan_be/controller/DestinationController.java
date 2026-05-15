package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.DestinationDto;
import com.vivuplan.vivuplan_be.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping
    public ResponseEntity<List<DestinationDto.DestinationResponse>> destinations(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Boolean featured) {
        return ResponseEntity.ok(destinationService.getDestinations(q, region, featured));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<DestinationDto.DestinationResponse>> featuredDestinations() {
        return ResponseEntity.ok(destinationService.getFeaturedDestinations());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<DestinationDto.DestinationResponse> destination(@PathVariable String slug) {
        return ResponseEntity.ok(destinationService.getBySlug(slug));
    }
}
