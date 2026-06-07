package com.crypto.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonTextMapper {
    private static final String EMPTY_ARRAY_JSON = "[]";

    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        if (value == null) {
            return EMPTY_ARRAY_JSON;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize value to JSON text", exception);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            String jsonToRead = json == null || json.isBlank() ? EMPTY_ARRAY_JSON : json;
            return objectMapper.readValue(jsonToRead, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize JSON text", exception);
        }
    }
}
