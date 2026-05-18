package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.DestinationDto;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private WeatherService weatherService;

    @Test
    void geocodeUsesDestinationCoordinatesBeforeExternalFallback() {
        DestinationService service = new DestinationService(destinationRepository, weatherService);
        Destination destination = Destination.builder()
                .name("Đà Nẵng")
                .slug("da-nang")
                .latitude(16.0767)
                .longitude(108.2228)
                .build();
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase("Đà Nẵng", "da-nang"))
                .thenReturn(Optional.of(destination));

        DestinationDto.LatLonResponse response = service.geocode("Đà Nẵng");

        assertThat(response.getLat()).isEqualTo(16.0767);
        assertThat(response.getLon()).isEqualTo(108.2228);
        verify(weatherService, never()).geocodeDestination("Đà Nẵng");
    }

    @Test
    void getWeatherMapsBackendWeatherFieldsForFrontend() {
        DestinationService service = new DestinationService(destinationRepository, weatherService);
        when(weatherService.getForecastForDestination("", 16.0767, 108.2228))
                .thenReturn(List.of(WeatherService.DailyWeather.builder()
                        .date("2026-05-19")
                        .code(63)
                        .maxTemp(31)
                        .minTemp(25)
                        .precipitationMm(12.5)
                        .precipitationProbability(80)
                        .windspeedKmh(36)
                        .build()));

        List<DestinationDto.WeatherDayResponse> response = service.getWeather(null, 16.0767, 108.2228);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getDate()).isEqualTo("2026-05-19");
        assertThat(response.get(0).getPrecipitationMm()).isEqualTo(12.5);
        assertThat(response.get(0).getWindspeedKmh()).isEqualTo(36);
    }
}
