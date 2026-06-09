package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import com.vivuplan.vivuplan_be.repository.LocationResolutionCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class LocationResolutionCacheCleanupService {

    private final LocationResolutionCacheRepository cacheRepository;
    private final int staleSuccessDays;
    private final int staleNegativeDays;
    private final int staleErrorDays;

    public LocationResolutionCacheCleanupService(
            LocationResolutionCacheRepository cacheRepository,
            @Value("${app.geocoding.cache-cleanup.stale-success-days:180}") int staleSuccessDays,
            @Value("${app.geocoding.cache-cleanup.stale-negative-days:30}") int staleNegativeDays,
            @Value("${app.geocoding.cache-cleanup.stale-error-days:7}") int staleErrorDays) {
        this.cacheRepository = cacheRepository;
        this.staleSuccessDays = staleSuccessDays;
        this.staleNegativeDays = staleNegativeDays;
        this.staleErrorDays = staleErrorDays;
    }

    @Scheduled(fixedDelayString = "${app.geocoding.cache-cleanup.scan-ms:86400000}")
    @Transactional
    public void cleanupStaleCache() {
        LocalDateTime now = LocalDateTime.now();

        // Xóa các bản ghi SUCCESS không được dùng quá lâu
        int deletedSuccess = cacheRepository.deleteByStatusInAndLastUsedAtBefore(
                List.of(LocationResolutionCache.Status.SUCCESS),
                now.minusDays(staleSuccessDays));

        // Xóa các bản ghi âm tính (NO_RESULT, LOW_CONFIDENCE) để cho hệ thống thử lại sau một thời gian
        int deletedNegative = cacheRepository.deleteByStatusInAndLastUsedAtBefore(
                List.of(LocationResolutionCache.Status.NO_RESULT, LocationResolutionCache.Status.LOW_CONFIDENCE),
                now.minusDays(staleNegativeDays));

        // Xóa các bản ghi lỗi cũ để hệ thống retry sớm
        int deletedError = cacheRepository.deleteByStatusInAndLastUsedAtBefore(
                List.of(LocationResolutionCache.Status.ERROR),
                now.minusDays(staleErrorDays));

        if (deletedSuccess > 0 || deletedNegative > 0 || deletedError > 0) {
            log.info(
                    "Geocode cache cleanup: deletedSuccess={} (>{} days), deletedNegative={} (>{} days), deletedError={} (>{} days)",
                    deletedSuccess, staleSuccessDays,
                    deletedNegative, staleNegativeDays,
                    deletedError, staleErrorDays);
        }
    }
}
