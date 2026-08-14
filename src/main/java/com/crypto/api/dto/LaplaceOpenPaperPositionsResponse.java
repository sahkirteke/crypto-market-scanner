package com.crypto.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record LaplaceOpenPaperPositionsResponse(
        List<LaplaceOpenPaperPositionResponse> positions,
        int openPositionCount,
        BigDecimal totalCurrentPnlUsdt,
        BigDecimal closedPositionsNetPnlUsdt,
        BigDecimal openPositionsCurrentPnlUsdt,
        BigDecimal totalPnlUsdt
) {
    public LaplaceOpenPaperPositionsResponse(List<LaplaceOpenPaperPositionResponse> positions,
                                             int openPositionCount, BigDecimal totalCurrentPnlUsdt) {
        this(positions, openPositionCount, totalCurrentPnlUsdt, BigDecimal.ZERO,
                totalCurrentPnlUsdt, totalCurrentPnlUsdt);
    }
}
