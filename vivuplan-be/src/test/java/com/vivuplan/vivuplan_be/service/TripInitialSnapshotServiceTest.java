package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.TripInitialSnapshot;
import com.vivuplan.vivuplan_be.repository.TripInitialSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripInitialSnapshotServiceTest {

    @Mock
    private TripInitialSnapshotRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsImmutableSnapshotFromAcceptedNormalizedTrip() {
        Trip trip = Trip.builder().id(42L).build();
        TripDto.TripResponse response = response(42L, "Ha Giang");
        TripInitialSnapshotService service = new TripInitialSnapshotService(repository, objectMapper);
        when(repository.findByTripId(42L)).thenReturn(Optional.empty());

        service.create(trip, response, "request-42", "gemini-test");

        ArgumentCaptor<TripInitialSnapshot> captor = ArgumentCaptor.forClass(TripInitialSnapshot.class);
        verify(repository).save(captor.capture());
        TripInitialSnapshot saved = captor.getValue();
        assertThat(saved.getTrip()).isSameAs(trip);
        assertThat(saved.getAiRequestId()).isEqualTo("request-42");
        assertThat(saved.getModel()).isEqualTo("gemini-test");
        assertThat(saved.getNormalizedSnapshot()).contains("\"destination\":\"Ha Giang\"");
        assertThat(trip.getInitialSnapshot()).isSameAs(saved);
    }

    @Test
    void rejectsMissingNormalizedSnapshot() {
        TripInitialSnapshotService service = new TripInitialSnapshotService(repository, objectMapper);

        assertThatThrownBy(() -> service.create(
                Trip.builder().id(42L).build(),
                null,
                "request-42",
                "gemini-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Normalized trip snapshot");
    }

    @Test
    void returnsNullForLegacyTripWithoutSnapshot() {
        TripInitialSnapshotService service = new TripInitialSnapshotService(repository, objectMapper);
        when(repository.findByTripId(9L)).thenReturn(Optional.empty());

        assertThat(service.getForAdmin(9L)).isNull();
    }

    @Test
    void returnsNormalizedSnapshotAndAuditMetadataForAdmin() throws Exception {
        Trip trip = Trip.builder().id(42L).build();
        TripDto.TripResponse response = response(42L, "Ha Giang");
        TripInitialSnapshot snapshot = TripInitialSnapshot.builder()
                .trip(trip)
                .normalizedSnapshot(objectMapper.writeValueAsString(response))
                .aiRequestId("request-42")
                .model("gemini-test")
                .createdAt(LocalDateTime.of(2026, 7, 21, 10, 30))
                .build();
        TripInitialSnapshotService service = new TripInitialSnapshotService(repository, objectMapper);
        when(repository.findByTripId(42L)).thenReturn(Optional.of(snapshot));

        AdminDto.TripInitialSnapshot result = service.getForAdmin(42L);

        assertThat(result.getTrip().getDestination()).isEqualTo("Ha Giang");
        assertThat(result.getAiRequestId()).isEqualTo("request-42");
        assertThat(result.getCreatedAt()).isEqualTo("2026-07-21T10:30");
    }

    private TripDto.TripResponse response(Long id, String destination) {
        TripDto.TripResponse response = new TripDto.TripResponse();
        response.setId(id);
        response.setDestination(destination);
        response.setSchedule(List.of());
        return response;
    }
}
