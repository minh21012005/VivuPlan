package com.vivuplan.vivuplan_be.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.entity.Place;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.PlaceRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.data-initializer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final DestinationRepository destinationRepository;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRole(Role.RoleName.USER, "Standard user");
        ensureRole(Role.RoleName.ADMIN, "System administrator");
        ensureDestinations();
        ensurePlaces();
    }

    private void ensureRole(Role.RoleName roleName, String description) {
        if (!roleRepository.existsByName(roleName)) {
            roleRepository.save(Role.builder()
                    .name(roleName)
                    .description(description)
                    .build());
        }
    }

    private void ensureDestinations() {
        ClassPathResource resource = new ClassPathResource("data/destinations.seed.json");
        if (!resource.exists()) {
            log.warn("Destination seed file not found");
            return;
        }

        try {
            List<DestinationSeed> seeds = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            for (DestinationSeed seed : seeds) {
                Destination destination = destinationRepository.findBySlug(seed.slug())
                        .orElseGet(Destination::new);
                applySeed(destination, seed);
                destinationRepository.save(destination);
            }
            log.info("Destination seed synchronized: {} records", seeds.size());
        } catch (IOException e) {
            throw new IllegalStateException("Không thể import dữ liệu điểm đến", e);
        }
    }

    private void applySeed(Destination destination, DestinationSeed seed) {
        destination.setName(seed.name());
        destination.setSlug(seed.slug());
        destination.setRegion(seed.region());
        destination.setTourismRegion(seed.tourismRegion());
        destination.setProvince(seed.province());
        destination.setCategory(seed.category());
        destination.setTag(seed.tag());
        destination.setRecommendedDays(seed.recommendedDays());
        destination.setRating(seed.rating());
        destination.setTripCount(seed.tripCount());
        destination.setImageUrl(seed.imageUrl());
        destination.setSummary(seed.summary());
        destination.setDescription(seed.description());
        destination.setBestTimeToVisit(seed.bestTimeToVisit());
        destination.setEstimatedBudgetMin(seed.estimatedBudgetMin());
        destination.setEstimatedBudgetMax(seed.estimatedBudgetMax());
        destination.setLatitude(seed.latitude());
        destination.setLongitude(seed.longitude());
        destination.setTags(new ArrayList<>(seed.tags() == null ? List.of() : seed.tags()));
        destination.setFeatured(Boolean.TRUE.equals(seed.featured()));
        destination.setActive(seed.active() == null || seed.active());
        destination.setDisplayOrder(seed.displayOrder());
        destination.setSourceName(seed.sourceName());
        destination.setSourceUrl(seed.sourceUrl());
    }

    private void ensurePlaces() {
        ClassPathResource resource = new ClassPathResource("data/places.seed.json");
        if (!resource.exists()) {
            log.warn("Place seed file not found");
            return;
        }

        try {
            List<PlaceSeed> seeds = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            for (PlaceSeed seed : seeds) {
                Place place = resolveSeedPlace(seed);
                applySeed(place, seed);
                placeRepository.save(place);
            }
            log.info("Place seed synchronized: {} records", seeds.size());
        } catch (IOException e) {
            throw new IllegalStateException("Không thể import dữ liệu địa điểm", e);
        }
    }

    private Place resolveSeedPlace(PlaceSeed seed) {
        if (seed.googlePlaceId() != null && !seed.googlePlaceId().isBlank()) {
            return placeRepository.findByGooglePlaceId(seed.googlePlaceId()).orElseGet(Place::new);
        }
        return placeRepository.findByDestinationIgnoreCaseAndNameIgnoreCase(seed.destination(), seed.name())
                .orElseGet(Place::new);
    }

    private void applySeed(Place place, PlaceSeed seed) {
        place.setName(seed.name());
        place.setDestination(seed.destination());
        place.setType(seed.type());
        place.setAddress(seed.address());
        place.setPriceLevel(seed.priceLevel());
        place.setEstimatedCostMin(seed.estimatedCostMin());
        place.setEstimatedCostMax(seed.estimatedCostMax());
        place.setRating(seed.rating());
        place.setLatitude(seed.latitude());
        place.setLongitude(seed.longitude());
        place.setGooglePlaceId(seed.googlePlaceId());
        place.setImageUrl(seed.imageUrl());
        place.setDescription(seed.description());
        place.setOpeningHours(seed.openingHours());
        place.setCostNote(seed.costNote());
        place.setIndoorOutdoor(seed.indoorOutdoor() != null ? seed.indoorOutdoor() : inferIndoorOutdoor(seed));
        place.setWeatherSensitivity(seed.weatherSensitivity() != null ? seed.weatherSensitivity() : inferWeatherSensitivity(seed));
        place.setCostBasis(seed.costBasis() != null ? seed.costBasis() : inferCostBasis(seed));
        place.setTags(new ArrayList<>(seed.tags() == null || seed.tags().isEmpty() ? inferTags(seed) : seed.tags()));
        place.setAliases(new ArrayList<>(seed.aliases() == null ? inferAliases(seed) : seed.aliases()));
        place.setVerified(seed.verified() == null || seed.verified());
        place.setSource(seed.source());
        place.setSourceUrl(seed.sourceUrl());
        place.setVerifiedAt(seed.verifiedAt());
    }

    private Place.IndoorOutdoor inferIndoorOutdoor(PlaceSeed seed) {
        String text = seedText(seed);
        if (seed.type() == Place.PlaceType.FOOD
                || seed.type() == Place.PlaceType.CAFE
                || seed.type() == Place.PlaceType.ACCOMMODATION
                || seed.type() == Place.PlaceType.NIGHTLIFE) {
            return containsAny(text, "cho", "pho di bo", "bai bien", "bai tam", "vinh", "vuon") ? Place.IndoorOutdoor.MIXED : Place.IndoorOutdoor.INDOOR;
        }
        if (containsAny(text, "bao tang", "nha co", "dinh", "nha tho", "chua", "den", "thap", "cho", "trung tam")) {
            return Place.IndoorOutdoor.MIXED;
        }
        if (containsAny(text, "bai bien", "bai tam", "vinh", "hon", "thuyen", "kayak", "sup", "deo", "thac", "nui", "ho", "rung", "trekking", "hang", "doi cat", "cao nguyen")) {
            return Place.IndoorOutdoor.OUTDOOR;
        }
        return Place.IndoorOutdoor.MIXED;
    }

    private Place.WeatherSensitivity inferWeatherSensitivity(PlaceSeed seed) {
        String text = seedText(seed);
        if (isMostlySpiritualOrHistorical(seed, text)) {
            return inferIndoorOutdoor(seed) == Place.IndoorOutdoor.INDOOR
                    ? Place.WeatherSensitivity.LOW
                    : Place.WeatherSensitivity.MEDIUM;
        }
        if (containsAny(text,
                "sup", "kayak", "cano", "ca no", "thuyen", "du thuyen", "tour tau", "tau cao toc",
                "lan bien", "tam bien", "trekking", "thac", "leo nui", "zipline", "du luon", "nhay du")) {
            return Place.WeatherSensitivity.HIGH;
        }
        Place.IndoorOutdoor indoorOutdoor = inferIndoorOutdoor(seed);
        if (indoorOutdoor == Place.IndoorOutdoor.INDOOR) {
            return Place.WeatherSensitivity.LOW;
        }
        return Place.WeatherSensitivity.MEDIUM;
    }

    private Place.CostBasis inferCostBasis(PlaceSeed seed) {
        long maxCost = seed.estimatedCostMax() != null ? Math.max(0, seed.estimatedCostMax()) : 0;
        if (maxCost == 0) {
            return Place.CostBasis.FREE;
        }
        return switch (seed.type()) {
            case ACCOMMODATION -> Place.CostBasis.PER_NIGHT;
            case TRANSPORT -> Place.CostBasis.PER_RIDE;
            case FOOD, CAFE, ATTRACTION, ACTIVITY, NIGHTLIFE -> Place.CostBasis.PER_PERSON;
        };
    }

    private List<String> inferTags(PlaceSeed seed) {
        Set<String> tags = new LinkedHashSet<>();
        if (seed.type() != null) {
            tags.add(seed.type().name().toLowerCase(Locale.ROOT));
        }
        Place.IndoorOutdoor indoorOutdoor = inferIndoorOutdoor(seed);
        tags.add(indoorOutdoor.name().toLowerCase(Locale.ROOT));
        String text = seedText(seed);
        addTagIf(tags, text, "beach", "bai bien", "bai tam", "bai sao", "bai xep", "bai sau", "bai cat", "bai cay", "bai dam", "bai rach", "vinh");
        if (isIslandPlace(seed, text)) {
            tags.add("island");
        }
        addTagIf(tags, text, "boat", "thuyen", "du thuyen", "tour tau", "tau cao toc", "cano", "ca no", "kayak", "sup");
        addTagIf(tags, text, "mountain", "nui", "deo", "cao nguyen", "trekking");
        addTagIf(tags, text, "waterfall", "thac");
        addTagIf(tags, text, "museum", "bao tang", "trung bay");
        addTagIf(tags, text, "heritage", "unesco", "pho co", "di san", "di tich");
        addTagIf(tags, text, "spiritual", "chua", "den", "mieu", "nha tho", "thien vien");
        addTagIf(tags, text, "food", "cho", "am thuc", "hai san", "dac san", "mon");
        addTagIf(tags, text, "family", "gia dinh", "bao tang", "cong vien", "vuon");
        addTagIf(tags, text, "couple", "hoang hon", "ngam canh", "view", "di dao");
        addTagIf(tags, text, "adventure", "zipline", "trekking", "kayak", "sup", "leo", "xe dia hinh");
        return List.copyOf(tags);
    }

    private List<String> inferAliases(PlaceSeed seed) {
        Set<String> aliases = new LinkedHashSet<>();
        String name = seed.name() == null ? "" : seed.name();
        String normalized = normalizeText(name);
        if (normalized.contains("dinh doc lap")) {
            aliases.add("Hội trường Thống Nhất");
        }
        if (normalized.contains("bao tang chung tich chien tranh")) {
            aliases.add("War Remnants Museum");
        }
        if (normalized.contains("cho ben thanh")) {
            aliases.add("Ben Thanh Market");
        }
        return List.copyOf(aliases);
    }

    private void addTagIf(Set<String> tags, String text, String tag, String... needles) {
        if (containsAny(text, needles)) {
            tags.add(tag);
        }
    }

    private boolean isMostlySpiritualOrHistorical(PlaceSeed seed, String text) {
        if (seed.type() == Place.PlaceType.ACTIVITY) {
            return false;
        }
        return containsAny(text, "chua", "den", "mieu", "nha tho", "dinh", "di tich", "nha tu");
    }

    private boolean isIslandPlace(PlaceSeed seed, String text) {
        String destination = seed.destination() == null ? "" : seed.destination().trim();
        String normalizedName = normalizeText(seed.name()).replaceAll("[^a-z0-9]+", " ").trim();
        return Set.of("Phú Quốc", "Cát Bà", "Lý Sơn", "Nam Du", "Côn Đảo", "Đảo Phú Quý").contains(destination)
                || normalizedName.startsWith("dao ")
                || normalizedName.startsWith("hon ");
    }

    private boolean containsAny(String text, String... needles) {
        String haystack = " " + normalizeText(text).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim() + " ";
        for (String needle : needles) {
            String normalizedNeedle = normalizeText(needle)
                    .replaceAll("[^a-z0-9]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!normalizedNeedle.isBlank() && haystack.contains(" " + normalizedNeedle + " ")) {
                return true;
            }
        }
        return false;
    }

    private String seedText(PlaceSeed seed) {
        return normalizeText(String.join(" ",
                seed.name() == null ? "" : seed.name(),
                seed.description() == null ? "" : seed.description(),
                seed.openingHours() == null ? "" : seed.openingHours()));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record DestinationSeed(
            String name,
            String slug,
            Destination.Region region,
            String tourismRegion,
            String province,
            Destination.DestinationCategory category,
            String tag,
            String recommendedDays,
            Double rating,
            Integer tripCount,
            String imageUrl,
            String summary,
            String description,
            String bestTimeToVisit,
            Long estimatedBudgetMin,
            Long estimatedBudgetMax,
            Double latitude,
            Double longitude,
            List<String> tags,
            Boolean featured,
            Boolean active,
            Integer displayOrder,
            String sourceName,
            String sourceUrl
    ) {}

    private record PlaceSeed(
            String name,
            String destination,
            Place.PlaceType type,
            String address,
            String priceLevel,
            Long estimatedCostMin,
            Long estimatedCostMax,
            Double rating,
            Double latitude,
            Double longitude,
            String googlePlaceId,
            String imageUrl,
            String description,
            String openingHours,
            String costNote,
            Place.IndoorOutdoor indoorOutdoor,
            Place.WeatherSensitivity weatherSensitivity,
            List<String> tags,
            List<String> aliases,
            Place.CostBasis costBasis,
            Boolean verified,
            String source,
            String sourceUrl,
            String verifiedAt
    ) {}
}
