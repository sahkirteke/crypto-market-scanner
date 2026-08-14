package com.crypto.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record LaplaceOpenPaperPositionsResponse(
        List<LaplaceOpenPaperPositionResponse> positions,
        int openPositionCount,
        BigDecimal totalCurrentPnlUsdt
) {
}
