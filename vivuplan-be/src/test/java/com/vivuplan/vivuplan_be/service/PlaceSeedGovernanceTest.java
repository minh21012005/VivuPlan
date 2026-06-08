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
            "accommodation", "activity", "adventure", "attraction", "beach", "boat", "cafe", "cave", "couple",
            "family", "food", "heritage", "indoor", "island", "lake", "market", "mixed", "mountain", "museum",
            "nature", "nightlife", "outdoor", "seasonal", "shopping", "spiritual", "sport", "theme-park",
            "transport", "viewpoint", "waterfall", "wellness");
    private static final Set<String> ISLAND_DESTINATIONS = Set.of(
            "Phú Quốc", "Cát Bà", "Lý Sơn", "Nam Du", "Côn Đảo", "Đảo Phú Quý");
    private static final List<String> HIGH_WEATHER_SIGNALS = List.of(
            "sup", "kayak", "cano", "ca no", "thuyen", "boat", "du thuyen", "tour tau", "tau cao toc",
            "tau", "tuyen tau", "tuyen tham quan", "thoi tiet",
            "lan bien", "tam bien", "trekking", "thac", "leo nui", "zipline", "du luon", "nhay du",
            "deo", "gio", "bien", "dao", "hang", "nui", "doi cat", "vuon quoc gia", "rung", "song",
            "cang", "turbine");

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
            assertOptionalVerificationFields(place, name);

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
            if ("INCLUDED".equals(costBasis)) {
                assertThat(place.path("costNote").asText(""))
                        .as("INCLUDED costBasis needs explanation for " + name)
                        .isNotBlank();
            }
            assertThatNaturalLanguageFieldsAreVietnamese(place, name);

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

        assertSourceVerifiedPlace(places, "Hạ Long", "Vịnh Hạ Long",
                "https://halongbay.com.vn/en/p/58-muc-phi-tham-quan-vinh-ha-long",
                200_000L,
                250_000L,
                "PER_PERSON",
                "HIGH");
        assertSourceVerifiedPlace(places, "Hạ Long", "Hang Sửng Sốt",
                "https://halongbay.com.vn/p/71-thoi-gian-don-tra-khach-ve-cang-ben",
                0L,
                0L,
                "INCLUDED",
                "HIGH");
        assertSourceVerifiedPlace(places, "Hạ Long", "Sun World Hạ Long",
                "https://sunworld.vn/vi/ha-long/check-in/bang-gia-sun-world-ha-long",
                300_000L,
                600_000L,
                "PER_PERSON",
                "MEDIUM");

        assertThat(findPlace(places, "Phong Nha - Kẻ Bàng", "Động Tiên Sơn").path("address").asText())
                .endsWith("Quảng Bình");
        assertThat(findPlace(places, "Phong Nha - Kẻ Bàng", "Vườn thực vật Phong Nha").path("address").asText())
                .endsWith("Quảng Bình");
        assertThat(findPlace(places, "Phong Nha - Kẻ Bàng", "Suối Nước Moọc").path("address").asText())
                .endsWith("Quảng Bình");
    }

    private void assertOptionalVerificationFields(JsonNode place, String name) {
        boolean hasSourceUrl = place.hasNonNull("sourceUrl") && !place.path("sourceUrl").asText().isBlank();
        boolean hasVerifiedAt = place.hasNonNull("verifiedAt") && !place.path("verifiedAt").asText().isBlank();
        assertThat(hasSourceUrl).as("sourceUrl and verifiedAt must be paired for " + name).isEqualTo(hasVerifiedAt);
        if (hasSourceUrl) {
            assertThat(place.path("sourceUrl").asText()).as("sourceUrl for " + name).startsWith("https://");
            assertThat(place.path("verifiedAt").asText()).as("verifiedAt for " + name)
                    .matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    private void assertThatNaturalLanguageFieldsAreVietnamese(JsonNode place, String name) {
        List<String> fields = List.of("openingHours", "costNote");
        List<String> englishFragments = List.of(
                "daily visit",
                "boat departure",
                "depends on",
                "subject to",
                "operating hours",
                "official sightseeing",
                "does not include",
                "route stop",
                "standalone attraction",
                "optional ",
                "may cost extra",
                "per adult");
        for (String field : fields) {
            String value = place.path(field).asText("");
            String normalized = normalizeText(value);
            englishFragments.forEach(fragment -> assertThat(containsPhrase(normalized, fragment))
                    .as(field + " should be Vietnamese for " + name + ": " + value)
                    .isFalse());
        }
    }

    private void assertSourceVerifiedPlace(
            JsonNode places,
            String destination,
            String name,
            String sourceUrl,
            long minCost,
            long maxCost,
            String costBasis,
            String weatherSensitivity) {
        JsonNode place = findPlace(places, destination, name);
        assertThat(place.path("sourceUrl").asText()).as("sourceUrl for " + name).isEqualTo(sourceUrl);
        assertThat(place.path("verifiedAt").asText()).as("verifiedAt for " + name).isNotBlank();
        assertThat(place.path("estimatedCostMin").asLong()).as("estimatedCostMin for " + name).isEqualTo(minCost);
        assertThat(place.path("estimatedCostMax").asLong()).as("estimatedCostMax for " + name).isEqualTo(maxCost);
        assertThat(place.path("costBasis").asText()).as("costBasis for " + name).isEqualTo(costBasis);
        assertThat(place.path("weatherSensitivity").asText()).as("weatherSensitivity for " + name).isEqualTo(weatherSensitivity);
        assertThat(place.path("costNote").asText()).as("costNote for " + name).isNotBlank();
    }

    private JsonNode findPlace(JsonNode places, String destination, String name) {
        for (JsonNode place : places) {
            if (destination.equals(place.path("destination").asText()) && name.equals(place.path("name").asText())) {
                return place;
            }
        }
        throw new AssertionError("Missing place " + destination + " / " + name);
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
                place.path("openingHours").asText(""),
                place.path("costNote").asText(""),
                tagsText(place)));
        return HIGH_WEATHER_SIGNALS.stream().anyMatch(signal -> containsPhrase(text, signal));
    }

    private String tagsText(JsonNode place) {
        StringBuilder tags = new StringBuilder();
        for (JsonNode tag : place.path("tags")) {
            tags.append(' ').append(tag.asText(""));
        }
        return tags.toString();
    }

    private boolean containsPhrase(String normalizedText, String phrase) {
        return (" " + normalizedText + " ").contains(" " + normalizeText(phrase) + " ");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("đ", "d")
                .replace("Đ", "D")
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
