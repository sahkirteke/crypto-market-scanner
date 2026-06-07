package com.crypto.api.dto;

import com.crypto.paper.model.PaperPositionEventType;
import java.math.BigDecimal;

public record PaperPositionEventResponse(
        Long id,
        Long positionId,
        String eventTime,
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
        String createdAt
) {}
