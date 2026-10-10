package com.shopsmart.kit.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for JdbcClient "listOfRows" results. JSONB columns should be selected as
 * {@code col::text} and then converted with {@link #json} so the API returns real JSON.
 */
public final class Rows {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Rows() {}

    public static JsonNode parse(Object text) {
        if (text == null) return null;
        try {
            return MAPPER.readTree(text.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Object> json(Map<String, Object> row, String... columns) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        for (String c : columns) {
            if (out.containsKey(c)) out.put(c, parse(out.get(c)));
        }
        return out;
    }

    public static List<Map<String, Object>> json(List<Map<String, Object>> rows, String... columns) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(json(r, columns));
        return out;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialise to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse JSON", e);
        }
    }
}
