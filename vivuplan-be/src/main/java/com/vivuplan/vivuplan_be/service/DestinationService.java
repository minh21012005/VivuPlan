package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.DestinationDto;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final WeatherService weatherService;

    public List<DestinationDto.DestinationResponse> getDestinations(String keyword, String region, Boolean featured) {
        List<Destination> destinations = Boolean.TRUE.equals(featured)
                ? destinationRepository.findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAscNameAsc()
                : destinationRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();

        String normalizedKeyword = normalize(keyword);
        Destination.Region requestedRegion = parseRegion(region);

        return destinations.stream()
                .filter(destination -> requestedRegion == null || destination.getRegion() == requestedRegion)
                .filter(destination -> normalizedKeyword.isBlank() || matchesKeyword(destination, normalizedKeyword))
                .map(DestinationDto.DestinationResponse::from)
                .collect(Collectors.toList());
    }

    public List<DestinationDto.DestinationResponse> getFeaturedDestinations() {
        return destinationRepository.findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(DestinationDto.DestinationResponse::from)
                .collect(Collectors.toList());
    }

    public DestinationDto.DestinationResponse getBySlug(String slug) {
        Destination destination = destinationRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy điểm đến"));
        return DestinationDto.DestinationResponse.from(destination);
    }

    public DestinationDto.LatLonResponse geocode(String destinationName) {
        if (destinationName == null || destinationName.isBlank()) {
            return null;
        }
        String trimmed = destinationName.trim();
        var dbDestination = destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(trimmed, toSlug(trimmed));
        if (dbDestination.isPresent()
                && dbDestination.get().getLatitude() != null
                && dbDestination.get().getLongitude() != null) {
            return DestinationDto.LatLonResponse.builder()
                    .lat(dbDestination.get().getLatitude())
                    .lon(dbDestination.get().getLongitude())
                    .build();
        }

        WeatherService.LatLon resolved = weatherService.geocodeDestination(trimmed);
        if (resolved == null) {
            return null;
        }
        return DestinationDto.LatLonResponse.builder()
                .lat(resolved.lat())
                .lon(resolved.lon())
                .build();
    }

    public List<DestinationDto.WeatherDayResponse> getWeather(String destinationName, Double lat, Double lon) {
        Double resolvedLat = lat;
        Double resolvedLon = lon;
        String trimmedDestination = destinationName != null ? destinationName.trim() : "";

        if ((resolvedLat == null || resolvedLon == null) && !trimmedDestination.isBlank()) {
            var dbDestination = destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(
                    trimmedDestination,
                    toSlug(trimmedDestination));
            if (dbDestination.isPresent()) {
                resolvedLat = dbDestination.get().getLatitude();
                resolvedLon = dbDestination.get().getLongitude();
            }
        }

        return weatherService.getForecastForDestination(trimmedDestination, resolvedLat, resolvedLon)
                .stream()
                .map(day -> DestinationDto.WeatherDayResponse.builder()
                        .date(day.getDate())
                        .code(day.getCode())
                        .maxTemp(day.getMaxTemp())
                        .minTemp(day.getMinTemp())
                        .precipitationMm(day.getPrecipitationMm())
                        .precipitationProbability(day.getPrecipitationProbability())
                        .windspeedKmh(day.getWindspeedKmh())
                        .build())
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(Destination destination, String keyword) {
        String searchableText = normalize(String.join(" ",
                nullToEmpty(destination.getName()),
                nullToEmpty(destination.getProvince()),
                nullToEmpty(destination.getTourismRegion()),
                destination.getRegion().getLabel(),
                destination.getCategory().name(),
                nullToEmpty(destination.getTag()),
                nullToEmpty(destination.getSummary()),
                nullToEmpty(destination.getDescription()),
                String.join(" ", destination.getTags())
        ));
        return searchableText.contains(keyword);
    }

    private Destination.Region parseRegion(String region) {
        String normalized = normalize(region);
        if (normalized.isBlank() || normalized.equals("tat ca")) return null;
        if (normalized.contains("bac") || normalized.equals("mien_bac")) return Destination.Region.MIEN_BAC;
        if (normalized.contains("trung") || normalized.equals("mien_trung")) return Destination.Region.MIEN_TRUNG;
        if (normalized.contains("nam") || normalized.equals("mien_nam")) return Destination.Region.MIEN_NAM;
        return null;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String toSlug(String value) {
        return normalize(value)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
