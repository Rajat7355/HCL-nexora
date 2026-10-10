package com.shopsmart.kit.error;

import java.time.Instant;
import java.util.Map;

/** Unified error body: {code, message, details, timestamp}. */
public record ApiError(String code, String message, Map<String, String> details, Instant timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }
}
