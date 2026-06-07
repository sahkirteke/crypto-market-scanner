package com.crypto.api.dto;

import java.math.BigDecimal;

public record ManualClosePaperPositionRequest(
        BigDecimal exitPrice,
        String exitDetail
) {
}
