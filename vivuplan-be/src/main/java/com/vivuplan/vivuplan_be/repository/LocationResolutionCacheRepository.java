package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationResolutionCacheRepository extends JpaRepository<LocationResolutionCache, Long> {

    Optional<LocationResolutionCache> findByProviderAndNormalizedQuery(String provider, String normalizedQuery);

    @Modifying
    @Query("DELETE FROM LocationResolutionCache c WHERE c.status IN :statuses AND c.lastUsedAt < :before")
    int deleteByStatusInAndLastUsedAtBefore(
            @Param("statuses") List<LocationResolutionCache.Status> statuses,
            @Param("before") LocalDateTime before);
}
