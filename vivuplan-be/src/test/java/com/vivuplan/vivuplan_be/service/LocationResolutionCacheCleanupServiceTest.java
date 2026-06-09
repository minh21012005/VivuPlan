package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import com.vivuplan.vivuplan_be.repository.LocationResolutionCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationResolutionCacheCleanupServiceTest {

    @Mock
    private LocationResolutionCacheRepository cacheRepository;

    @Test
    void cleanupStaleCacheDeletesRecordsAccordingToTimeframes() {
        LocationResolutionCacheCleanupService service = new LocationResolutionCacheCleanupService(
                cacheRepository, 180, 30, 7
        );

        when(cacheRepository.deleteByStatusInAndLastUsedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(1)
                .thenReturn(2)
                .thenReturn(3);

        LocalDateTime approxNow = LocalDateTime.now();

        service.cleanupStaleCache();

        // Capture arguments to verify statuses and approximate times
        ArgumentCaptor<List<LocationResolutionCache.Status>> statusCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(cacheRepository).deleteByStatusInAndLastUsedAtBefore(
                eq(List.of(LocationResolutionCache.Status.SUCCESS)),
                timeCaptor.capture()
        );
        assertThat(timeCaptor.getValue()).isCloseTo(approxNow.minusDays(180), within(1, ChronoUnit.SECONDS));

        verify(cacheRepository).deleteByStatusInAndLastUsedAtBefore(
                eq(List.of(LocationResolutionCache.Status.NO_RESULT, LocationResolutionCache.Status.LOW_CONFIDENCE)),
                timeCaptor.capture()
        );
        assertThat(timeCaptor.getValue()).isCloseTo(approxNow.minusDays(30), within(1, ChronoUnit.SECONDS));

        verify(cacheRepository).deleteByStatusInAndLastUsedAtBefore(
                eq(List.of(LocationResolutionCache.Status.ERROR)),
                timeCaptor.capture()
        );
        assertThat(timeCaptor.getValue()).isCloseTo(approxNow.minusDays(7), within(1, ChronoUnit.SECONDS));
    }
}
