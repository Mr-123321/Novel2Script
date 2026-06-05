package com.novel2script.api.dto;

import java.time.Instant;

/**
 * Standard API response envelope.
 */
public record ApiResponse(
        int code,
        String message,
        Object data,
        long timestamp
) {
    public ApiResponse {
        if (timestamp == 0) {
            timestamp = Instant.now().toEpochMilli();
        }
    }

    public static ApiResponse success(Object data) {
        return new ApiResponse(200, "OK", data, Instant.now().toEpochMilli());
    }

    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(200, message, data, Instant.now().toEpochMilli());
    }

    public static ApiResponse error(int code, String message) {
        return new ApiResponse(code, message, null, Instant.now().toEpochMilli());
    }
}
