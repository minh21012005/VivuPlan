package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSeedGovernanceTest {

    private static final Set<String> TAG_TAXONOMY = Set.of(
            "accommodation", "activity", "adventure", "attraction", "beach", "boat", "cafe", "couple",
            "family", "food", "heritage", "indoor", "island", "mixed", "mountain", "museum",
            "nightlife", "outdoor", "spiritual", "transport", "waterfall");
    private static final Set<String> ISLAND_DESTINATIONS = Set.of(
            "Phú Quốc", "Cát Bà", "Lý Sơn", "Nam Du", "Côn Đảo", "Đảo Phú Quý");
    private static final List<String> HIGH_WEATHER_SIGNALS = List.of(
            "sup", "kayak", "cano", "ca no", "thuyen", "du thuyen", "tour tau", "tau cao toc",
            "lan bien", "tam bien", "trekking", "thac", "leo nui", "zipline", "du luon", "nhay du");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void placesSeedCoversDestinationsAndKeepsCoreFieldsValid() throws Exception {
        JsonNode destinations = objectMapper.readTree(Path.of("src/main/resources/data/destinations.seed.json").toFile());
        JsonNode places = objectMapper.readTree(Path.of("src/main/resources/data/places.seed.json").toFile());

        Set<String> destinationNames = new HashSet<>();
        destinations.forEach(destination -> destinationNames.add(destination.path("name").asText()));

        Map<String, Integer> placeCounts = new HashMap<>();
        Set<String> placeKeys = new HashSet<>();
        for (JsonNode place : places) {
            String destination = requiredText(place, "destination");
            String name = requiredText(place, "name");
            placeCounts.merge(destination, 1, Integer::sum);
            assertThat(destinationNames).as("unknown destination for place " + name).contains(destination);
            assertThat(placeKeys.add(destination.toLowerCase() + "::" + name.toLowerCase()))
                    .as("duplicate place " + destination + " / " + name)
                    .isTrue();

            assertThat(requiredText(place, "source")).as("source for " + name).isNotBlank();
            assertThat(requiredText(place, "type")).as("type for " + name)
                    .isIn("FOOD", "CAFE", "ATTRACTION", "ACCOMMODATION", "TRANSPORT", "ACTIVITY", "NIGHTLIFE");
            assertThat(requiredText(place, "priceLevel")).as("priceLevel for " + name)
                    .isIn("FREE", "LOW", "MID", "HIGH");
            assertThat(requiredText(place, "indoorOutdoor")).as("indoorOutdoor for " + name)
                    .isIn("INDOOR", "OUTDOOR", "MIXED");
            assertThat(requiredText(place, "weatherSensitivity")).as("weatherSensitivity for " + name)
                    .isIn("LOW", "MEDIUM", "HIGH");
            assertThat(place.has("bestTimeOfDay")).as("bestTimeOfDay must not be inferred in seed for " + name).isFalse();
            assertThat(requiredText(place, "costBasis")).as("costBasis for " + name)
                    .isIn("PER_PERSON", "GROUP", "PER_NIGHT", "PER_RIDE", "FREE", "INCLUDED");
            assertThat(place.has("recommendedDurationMinutes"))
                    .as("recommendedDurationMinutes must not be inferred in seed for " + name)
                    .isFalse();
            assertThat(place.has("tags") && place.path("tags").isArray()).as("tags array for " + name).isTrue();
            assertThat(place.path("tags").size()).as("tags coverage for " + name).isGreaterThan(0);
            place.path("tags").forEach(tag -> assertThat(tag.asText())
                    .as("known tag for " + name)
                    .isIn(TAG_TAXONOMY));
            assertThat(place.has("aliases") && place.path("aliases").isArray()).as("aliases array for " + name).isTrue();
            assertThat(place.has("sourceUrl")).as("sourceUrl requires per-place verification for " + name).isFalse();
            assertThat(place.has("verifiedAt")).as("verifiedAt requires per-place verification for " + name).isFalse();

            long minCost = place.path("estimatedCostMin").asLong(0);
            long maxCost = place.path("estimatedCostMax").asLong(minCost);
            assertThat(minCost).as("min cost for " + name).isGreaterThanOrEqualTo(0);
            assertThat(maxCost).as("max cost for " + name).isGreaterThanOrEqualTo(minCost);
            String costBasis = requiredText(place, "costBasis");
            String priceLevel = requiredText(place, "priceLevel");
            if (maxCost > 0) {
                assertThat(priceLevel).as("positive-cost place must not use FREE priceLevel for " + name)
                        .isNotEqualTo("FREE");
            }
            if ("FREE".equals(priceLevel)) {
                assertThat(maxCost).as("FREE priceLevel must have zero max cost for " + name).isZero();
            }
            if (maxCost > 0) {
                assertThat(costBasis).as("paid place must not use FREE costBasis for " + name).isNotEqualTo("FREE");
            }
            if ("FREE".equals(costBasis)) {
                assertThat(maxCost).as("FREE costBasis must have zero max cost for " + name).isZero();
            }

            String indoorOutdoor = requiredText(place, "indoorOutdoor");
            String weatherSensitivity = requiredText(place, "weatherSensitivity");
            if ("HIGH".equals(weatherSensitivity)) {
                assertThat(indoorOutdoor).as("indoor place cannot be high weather sensitivity for " + name)
                        .isNotEqualTo("INDOOR");
                assertThat(hasHighWeatherSignal(place)).as("HIGH weather signal for " + name).isTrue();
            }
            if (hasTag(place, "island") && !ISLAND_DESTINATIONS.contains(destination)) {
                String normalizedName = normalizeText(name);
                assertThat(normalizedName.startsWith("hon ") || normalizedName.startsWith("dao "))
                        .as("island tag should come from island destination or explicit island name for " + name)
                        .isTrue();
            }
            if (place.hasNonNull("latitude") || place.hasNonNull("longitude")) {
                assertThat(place.path("latitude").asDouble()).as("latitude for " + name).isBetween(-90.0, 90.0);
                assertThat(place.path("longitude").asDouble()).as("longitude for " + name).isBetween(-180.0, 180.0);
            }
        }

        assertThat(placeCounts.keySet()).containsAll(destinationNames);
        destinationNames.forEach(destination ->
                assertThat(placeCounts.getOrDefault(destination, 0))
                        .as("minimum place coverage for " + destination)
                        .isGreaterThanOrEqualTo(3));

        Map<String, Integer> lowCoverage = new HashMap<>();
        placeCounts.forEach((destination, count) -> {
            if (count < 5) {
                lowCoverage.put(destination, count);
            }
        });
        if (!lowCoverage.isEmpty()) {
            System.out.println("Place seed coverage warning (<5 POIs): " + lowCoverage);
        }
    }

    private String requiredText(JsonNode node, String field) {
        assertThat(node.hasNonNull(field)).as("missing field " + field + " in " + node).isTrue();
        return node.path(field).asText();
    }

    private boolean hasTag(JsonNode place, String tag) {
        for (JsonNode value : place.path("tags")) {
            if (tag.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHighWeatherSignal(JsonNode place) {
        String text = normalizeText(String.join(" ",
                place.path("name").asText(""),
                place.path("description").asText(""),
                place.path("openingHours").asText("")));
        return HIGH_WEATHER_SIGNALS.stream().anyMatch(signal -> containsPhrase(text, signal));
    }

    private boolean containsPhrase(String normalizedText, String phrase) {
        return (" " + normalizedText + " ").contains(" " + normalizeText(phrase) + " ");
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
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
