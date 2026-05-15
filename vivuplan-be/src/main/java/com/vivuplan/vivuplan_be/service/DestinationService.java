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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
