package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Place;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacePlanningServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Test
    void selectPromptPlacesRanksUserRequestAndRainSafeOptions() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(List.of(
                        place(1L, "SUP My Khe", Place.PlaceType.ACTIVITY, 4.9, 300_000, "Outdoor sea activity depends on wind"),
                        place(2L, "Da Nang Museum", Place.PlaceType.ATTRACTION, 4.4, 40_000, "Indoor museum for rainy days"),
                        place(3L, "Con Market", Place.PlaceType.FOOD, 4.2, 150_000, "Central food market"),
                        place(4L, "Hai Van Pass", Place.PlaceType.ATTRACTION, 4.8, 0, "Outdoor scenic mountain pass")));

        TripDto.GenerateRequest req = request();
        req.setNotes("toi muon museum va food");
        req.setWeatherForecast("Day 1 (2026-05-19): Thunderstorm, 24-29C, rain chance 80% -> HIGH RAIN RISK");

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).extracting(Place::getName)
                .contains("Con Market", "Da Nang Museum");
        assertThat(selected.indexOf(selected.stream()
                .filter(place -> "SUP My Khe".equals(place.getName()))
                .findFirst()
                .orElseThrow()))
                .isGreaterThan(selected.indexOf(selected.stream()
                        .filter(place -> "Da Nang Museum".equals(place.getName()))
                        .findFirst()
                        .orElseThrow()));
    }

    @Test
    void verifiedPlacesContextMarksHighRatedScenicAttractionsAsDestinationSignature() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        Place scenic = place(10L, "Trang An Scenic Landscape Complex", Place.PlaceType.ACTIVITY, 4.8, 250_000,
                "Signature boat and limestone landscape experience");
        scenic.setTags(List.of("boat", "heritage", "nature"));
        scenic.setIndoorOutdoor(Place.IndoorOutdoor.OUTDOOR);
        scenic.setWeatherSensitivity(Place.WeatherSensitivity.HIGH);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(List.of(scenic));

        String context = service.buildVerifiedPlacesContext(request());

        assertThat(context)
                .contains("Trang An Scenic Landscape Complex")
                .contains("priority=destination-signature");
    }

    @Test
    void verifiedPlacesContextMarksOldTownNightlifeAsDestinationSignature() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        Place oldTown = place(13L, "Pho co Hoa Lu", Place.PlaceType.ATTRACTION, 4.5, 150_000,
                "Old town evening walking street with food and cultural nightlife");
        oldTown.setTags(List.of("heritage", "nightlife", "walking"));
        oldTown.setIndoorOutdoor(Place.IndoorOutdoor.MIXED);
        oldTown.setWeatherSensitivity(Place.WeatherSensitivity.LOW);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(List.of(oldTown));

        String context = service.buildVerifiedPlacesContext(request());

        assertThat(context)
                .contains("Pho co Hoa Lu")
                .contains("priority=destination-signature");
    }

    @Test
    void selectPromptPlacesKeepsSignatureScenicExperienceHighDuringRainFlex() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        Place scenic = place(11L, "Trang An Scenic Landscape Complex", Place.PlaceType.ACTIVITY, 5.0, 250_000,
                "Signature boat and limestone landscape experience");
        scenic.setTags(List.of("boat", "heritage", "nature"));
        scenic.setIndoorOutdoor(Place.IndoorOutdoor.OUTDOOR);
        scenic.setWeatherSensitivity(Place.WeatherSensitivity.HIGH);
        Place indoor = place(12L, "Indoor Backup Gallery", Place.PlaceType.ATTRACTION, 4.0, 300_000,
                "Indoor gallery backup option");
        indoor.setIndoorOutdoor(Place.IndoorOutdoor.INDOOR);
        indoor.setWeatherSensitivity(Place.WeatherSensitivity.LOW);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(List.of(scenic, indoor));

        TripDto.GenerateRequest req = request();
        req.setWeatherForecast("Day 1 (2026-05-19): Rain, 25-30C, rain chance 75%, rain 4.0mm, wind 12km/h -> RAIN FLEX");

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected.indexOf(scenic)).isLessThan(selected.indexOf(indoor));
    }

    @Test
    void selectPromptPlacesKeepsAllCandidatesWhenWithinAdaptiveLimit() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        List<Place> candidates = places("Da Nang POI ", 1, 12, "da nang");
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(candidates);

        TripDto.GenerateRequest req = request();
        req.setDays(1);

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).hasSize(12);
    }

    @Test
    void selectPromptPlacesCapsShortTripsToAvoidOverloadingPrompt() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        List<Place> candidates = places("Da Nang POI ", 1, 28, "da nang");
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(candidates);

        TripDto.GenerateRequest req = request();
        req.setDays(1);

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).hasSize(14);
    }

    @Test
    void selectPromptPlacesBoostsLimitWhenUserHasSpecificRequest() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        List<Place> candidates = places("Da Nang POI ", 1, 28, "da nang");
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(candidates);

        TripDto.GenerateRequest req = request();
        req.setDays(1);
        req.setNotes("toi muon museum va seafood");

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).hasSize(18);
    }

    @Test
    void selectPromptPlacesCapsAndDiversifiesWhenCandidateListExceedsSafePromptLimit() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        List<Place> candidates = places("Da Nang POI ", 1, 35, "da nang");
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(candidates);

        TripDto.GenerateRequest req = request();
        req.setDays(5);

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).hasSize(30);
    }

    @Test
    void selectPromptPlacesCapsNearbyCandidatesToTwentyPercent() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        List<Place> mainPlaces = places("Da Nang Main POI ", 1, 24, "da nang");
        List<Place> nearbyPlaces = places("Hoi An Nearby POI ", 101, 112, "Hoi An");
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenAnswer(invocation -> "da nang".equals(invocation.getArgument(0))
                        ? mainPlaces
                        : nearbyPlaces);

        TripDto.GenerateRequest req = request();
        req.setDestination("da nang");
        req.setDays(4);

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).hasSize(26);
        assertThat(selected.stream().filter(place -> "Hoi An".equals(place.getDestination())).count()).isEqualTo(5);
    }

    @Test
    void enrichScheduleWithVerifiedPlacesAttachesMatchedPlaceData() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        Place museum = place(44L, "Da Nang Museum", Place.PlaceType.ATTRACTION, 4.5, 60_000, "Central museum");
        museum.setLatitude(16.0746);
        museum.setLongitude(108.2231);
        museum.setAddress("24 Tran Phu, Da Nang");
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(anyString()))
                .thenReturn(List.of(museum));

        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setName("Explore Da Nang Museum");
        activity.setType("ATTRACTION");
        activity.setLocation("Incorrect AI-provided province");
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setActivities(List.of(activity));

        service.enrichScheduleWithVerifiedPlaces(List.of(day), "da nang");

        assertThat(activity.getPlaceId()).isEqualTo(44L);
        assertThat(activity.getLatitude()).isEqualTo(16.0746);
        assertThat(activity.getLongitude()).isEqualTo(108.2231);
        assertThat(activity.getLocation()).isEqualTo("24 Tran Phu, Da Nang");
        assertThat(activity.getRating()).isEqualTo(4.5);
    }

    private TripDto.GenerateRequest request() {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDestination("da nang");
        req.setDeparture("ha noi");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now());
        req.setDays(1);
        req.setBudgetPerPerson(600_000L);
        req.setBudgetMode("PER_PERSON");
        req.setTravelerCount(1);
        req.setStyle("RELAXING");
        req.setGroupType("COUPLE");
        return req;
    }

    private List<Place> places(String namePrefix, int startInclusive, int endInclusive, String destination) {
        return java.util.stream.IntStream.rangeClosed(startInclusive, endInclusive)
                .mapToObj(index -> {
                    Place place = place(
                            (long) index,
                            namePrefix + index,
                            index % 3 == 0 ? Place.PlaceType.FOOD : Place.PlaceType.ATTRACTION,
                            4.0,
                            100_000,
                            "Useful itinerary candidate");
                    place.setDestination(destination);
                    return place;
                })
                .toList();
    }

    private Place place(Long id, String name, Place.PlaceType type, double rating, long maxCost, String description) {
        return Place.builder()
                .id(id)
                .name(name)
                .destination("da nang")
                .type(type)
                .address(name + ", da nang")
                .estimatedCostMin(0L)
                .estimatedCostMax(maxCost)
                .rating(rating)
                .description(description)
                .verified(true)
                .build();
    }
}
