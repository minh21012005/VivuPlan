package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationResolutionCacheRepository extends JpaRepository<LocationResolutionCache, Long> {
    Optional<LocationResolutionCache> findByProviderAndNormalizedQuery(String provider, String normalizedQuery);
}
