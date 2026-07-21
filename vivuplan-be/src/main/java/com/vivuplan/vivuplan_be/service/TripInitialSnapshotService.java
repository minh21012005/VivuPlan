package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.TripInitialSnapshot;
import com.vivuplan.vivuplan_be.repository.TripInitialSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripInitialSnapshotService {
    private final TripInitialSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(
            Trip trip,
            TripDto.TripResponse normalizedTrip,
            String aiRequestId,
            String model) {
        validateNewSnapshot(trip, normalizedTrip);
        save(trip, normalizedTrip, aiRequestId, model);
    }

    private void validateNewSnapshot(Trip trip, TripDto.TripResponse normalizedTrip) {
        if (trip == null || trip.getId() == null) {
            throw new IllegalArgumentException("Trip must already be persisted");
        }
        if (normalizedTrip == null) {
            throw new IllegalArgumentException("Normalized trip snapshot is required");
        }
        if (repository.findByTripId(trip.getId()).isPresent()) {
            throw new IllegalStateException("Initial snapshot already exists for trip " + trip.getId());
        }
    }

    private void save(
            Trip trip,
            TripDto.TripResponse normalizedTrip,
            String aiRequestId,
            String model) {
        TripInitialSnapshot snapshot = TripInitialSnapshot.builder()
                .trip(trip)
                .normalizedSnapshot(writeSnapshot(normalizedTrip))
                .aiRequestId(aiRequestId)
                .model(model)
                .build();
        trip.setInitialSnapshot(snapshot);
        repository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public AdminDto.TripInitialSnapshot getForAdmin(Long tripId) {
        return repository.findByTripId(tripId)
                .map(this::toAdminDto)
                .orElse(null);
    }

    private String writeSnapshot(TripDto.TripResponse trip) {
        try {
            return objectMapper.writeValueAsString(trip);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize initial trip snapshot", e);
        }
    }

    private AdminDto.TripInitialSnapshot toAdminDto(TripInitialSnapshot snapshot) {
        try {
            AdminDto.TripInitialSnapshot dto = new AdminDto.TripInitialSnapshot();
            dto.setTrip(objectMapper.readValue(snapshot.getNormalizedSnapshot(), TripDto.TripResponse.class));
            dto.setAiRequestId(snapshot.getAiRequestId());
            dto.setModel(snapshot.getModel());
            dto.setCreatedAt(snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null);
            return dto;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize initial trip snapshot for trip "
                    + snapshot.getTrip().getId(), e);
        }
    }

}
