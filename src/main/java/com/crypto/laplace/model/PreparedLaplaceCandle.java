package com.crypto.laplace.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PreparedLaplaceCandle(
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        Double regressionValue,
        Double atr14,
        Double slope,
        Double normalizedSlope) {
}
