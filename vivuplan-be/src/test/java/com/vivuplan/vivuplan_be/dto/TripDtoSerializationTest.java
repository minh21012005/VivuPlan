package com.vivuplan.vivuplan_be.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tripResponseSerializesVisibilityAsIsPublic() {
        TripDto.TripResponse response = new TripDto.TripResponse();
        response.setId(1L);
        response.setDestination("Ba Vi");
        response.setPublic(true);
        response.setShareCode("S123456789");

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("isPublic").asBoolean()).isTrue();
        assertThat(json.has("public")).isFalse();
    }
}
