package com.vivuplan.vivuplan_be.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.data-initializer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final DestinationRepository destinationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRole(Role.RoleName.USER, "Standard user");
        ensureRole(Role.RoleName.ADMIN, "System administrator");
        ensureDestinations();
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
}
