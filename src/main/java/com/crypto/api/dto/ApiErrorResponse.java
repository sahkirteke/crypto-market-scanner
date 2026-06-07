package com.crypto.api.dto;


public record ApiErrorResponse(
        boolean success,
        String message,
        String code,
        String timestamp
) {
}
