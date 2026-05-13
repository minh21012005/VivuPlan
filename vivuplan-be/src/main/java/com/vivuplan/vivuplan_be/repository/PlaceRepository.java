package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    List<Place> findByDestinationAndVerifiedTrueOrderByRatingDesc(String destination);
    List<Place> findByDestinationAndTypeAndVerifiedTrueOrderByRatingDesc(String destination, Place.PlaceType type);
    Optional<Place> findByGooglePlaceId(String googlePlaceId);
}
