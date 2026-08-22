package com.crypto.laplace.model;

import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceMarketShockContext(
        LaplaceMarketShockDirection direction,
        Instant candidateCandleCloseTime,
        Instant confirmationCandleCloseTime,
        BigDecimal candidateBreadthPct,
        BigDecimal continuationBreadthPct,
        int totalOpenPositions,
        int oppositeOpenPositions,
        BigDecimal oppositeRatio) { }
