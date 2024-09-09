package com.doppelganger113.commandrunner.batching.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

@Converter
public class JsonHashMapConverter implements AttributeConverter<HashMap<String, Object>, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(JsonHashMapConverter.class);

    @Override
    public String convertToDatabaseColumn(HashMap<String, Object> stringObjectHashMap) {
        try {
            return objectMapper.writeValueAsString(stringObjectHashMap);
        } catch (JsonProcessingException jpe) {
            log.warn("Cannot convert HashMap<String, Object> into JSON");
            return "";
        }
    }

    @Override
    public HashMap<String, Object> convertToEntityAttribute(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert JSON into HashMap<String, Object>");
            return null;
        }
    }
}
