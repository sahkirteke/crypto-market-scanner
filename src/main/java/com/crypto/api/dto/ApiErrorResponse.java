package com.crypto.api.dto;

import java.time.Instant;

public record ApiErrorResponse(
        boolean success,
        String message,
        String code,
        Instant timestamp
) {
}
