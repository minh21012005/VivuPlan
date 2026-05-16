package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {
    List<Destination> findByActiveTrueOrderByDisplayOrderAscNameAsc();
    List<Destination> findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAscNameAsc();
    Optional<Destination> findBySlugAndActiveTrue(String slug);
    Optional<Destination> findBySlug(String slug);
    /** Used by WeatherService context resolution — targeted lookup, no full-table scan. */
    Optional<Destination> findByNameIgnoreCaseOrSlugIgnoreCase(String name, String slug);
}
