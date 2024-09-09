package com.doppelganger113.commandrunner.hash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

class ShaHashTest {

    private final ShaHash shaHash = new ShaHash();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"John\", \"age\": 32, \"nested\": {\"success\": true}}",
            "{\"nested\": {\"success\": true}, \"age\": 32, \"name\":\"John\"}",
            "{\"nested\": {\"success\": true, \"empty\": null}, \"age\": 32, \"name\":\"John\"}",
    })
    void testHashToProduceSameHashWithDifferentJsonOrder(String jsonValue) throws JsonProcessingException {
        HashMap<String, Object> map = objectMapper.readValue(jsonValue, new TypeReference<>() {
        });

        Assertions.assertEquals(
                "d8987bfd10bd8057a6fb31a4378d527f91fbfcf60d7ddb421cf2ef23215381ec",
                shaHash.hash(map)
        );
    }

    @Test
    void testHashToHandleNull() {
        Assertions.assertEquals("", shaHash.hash(null));
    }
}