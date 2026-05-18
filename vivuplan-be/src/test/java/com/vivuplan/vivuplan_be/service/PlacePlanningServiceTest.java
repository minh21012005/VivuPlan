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
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase("Đà Nẵng", "da-nang"))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc("Đà Nẵng"))
                .thenReturn(List.of(
                        place(1L, "Chèo SUP Mỹ Khê", Place.PlaceType.ACTIVITY, 4.9, 300_000, "Hoạt động ngoài biển, phụ thuộc sóng gió"),
                        place(2L, "Bảo tàng Đà Nẵng", Place.PlaceType.ATTRACTION, 4.4, 40_000, "Bảo tàng trong nhà phù hợp ngày mưa"),
                        place(3L, "Chợ Cồn", Place.PlaceType.FOOD, 4.2, 150_000, "Chợ ẩm thực trong trung tâm"),
                        place(4L, "Đèo Hải Vân", Place.PlaceType.ATTRACTION, 4.8, 0, "Cung đèo ngắm cảnh ngoài trời")));

        TripDto.GenerateRequest req = request();
        req.setNotes("Tôi muốn bảo tàng và ăn đặc sản địa phương");
        req.setWeatherForecast("Day 1 (2026-05-19): Thunderstorm, 24-29°C, rain chance 80% -> HIGH RAIN RISK");

        List<Place> selected = service.selectPromptPlaces(req);

        assertThat(selected).extracting(Place::getName)
                .containsSubsequence("Bảo tàng Đà Nẵng", "Chợ Cồn")
                .doesNotContainSequence("Chèo SUP Mỹ Khê", "Bảo tàng Đà Nẵng");
    }

    @Test
    void enrichScheduleWithVerifiedPlacesAttachesMatchedPlaceData() {
        PlacePlanningService service = new PlacePlanningService(placeRepository, destinationRepository);
        Place museum = place(44L, "Bảo tàng Đà Nẵng", Place.PlaceType.ATTRACTION, 4.5, 60_000, "Bảo tàng trung tâm");
        museum.setLatitude(16.0746);
        museum.setLongitude(108.2231);
        museum.setAddress("24 Trần Phú, Hải Châu, Đà Nẵng");
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase("Đà Nẵng", "da-nang"))
                .thenReturn(Optional.empty());
        when(placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc("Đà Nẵng"))
                .thenReturn(List.of(museum));

        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setName("Khám phá Bảo tàng Đà Nẵng");
        activity.setType("ATTRACTION");
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setActivities(List.of(activity));

        service.enrichScheduleWithVerifiedPlaces(List.of(day), "Đà Nẵng");

        assertThat(activity.getPlaceId()).isEqualTo(44L);
        assertThat(activity.getLatitude()).isEqualTo(16.0746);
        assertThat(activity.getLongitude()).isEqualTo(108.2231);
        assertThat(activity.getLocation()).isEqualTo("24 Trần Phú, Hải Châu, Đà Nẵng");
        assertThat(activity.getRating()).isEqualTo(4.5);
    }

    private TripDto.GenerateRequest request() {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDestination("Đà Nẵng");
        req.setDeparture("Hà Nội");
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

    private Place place(Long id, String name, Place.PlaceType type, double rating, long maxCost, String description) {
        return Place.builder()
                .id(id)
                .name(name)
                .destination("Đà Nẵng")
                .type(type)
                .address(name + ", Đà Nẵng")
                .estimatedCostMin(0L)
                .estimatedCostMax(maxCost)
                .rating(rating)
                .description(description)
                .verified(true)
                .build();
    }
}
