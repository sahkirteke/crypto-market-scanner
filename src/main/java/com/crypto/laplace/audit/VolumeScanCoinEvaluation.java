package com.crypto.laplace.audit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record VolumeScanCoinEvaluation(
        String symbol, VolumeScanAuditStatus previousStatus, VolumeScanAuditStatus newStatus,
        VolumeScanTransition transition, boolean eligible, boolean includedInPool,
        String primaryReasonCode, List<String> reasonCodes, List<String> failedRules,
        List<String> passedRules, OffsetDateTime evaluatedAt, BigDecimal quoteVolume24h,
        BigDecimal previousVolume24h, BigDecimal volumeChangePct, Integer volumeRank,
        BigDecimal lastPrice, BigDecimal priceChange24hPct, boolean positionOpen,
        boolean reentryBlocked, boolean dataComplete, String errorCode, String errorMessage) {}
