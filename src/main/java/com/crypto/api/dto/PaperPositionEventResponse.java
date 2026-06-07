package com.crypto.api.dto;

import com.crypto.paper.model.PaperPositionEventType;
import java.math.BigDecimal;
import java.time.Instant;

public record PaperPositionEventResponse(
        Long id,
        Long positionId,
        Instant eventTimeUtc,
        PaperPositionEventType eventType,
        BigDecimal price,
        BigDecimal adjustedPrice,
        BigDecimal positionPctClosed,
        BigDecimal rawPnlPct,
        BigDecimal netPnlPct,
        BigDecimal leveragedNetPnlPct,
        BigDecimal feePct,
        BigDecimal slippagePct,
        Integer leverage,
        String reason,
        String detailsJson,
        Instant createdAt
) {}
