package com.crypto.api.dto;

import java.math.BigDecimal;
import java.util.List;
import com.crypto.laplace.model.LaplacePaperVariant;
import com.crypto.laplace.model.LaplaceRuntimeState;
import java.time.Instant;

public record LaplaceOpenPaperPositionsResponse(
        LaplacePaperVariant paperVariant,
        boolean signalInverted,
        List<LaplaceOpenPaperPositionResponse> positions,
        int openPositionCount,
        int closedPositionCount,
        BigDecimal closedPositionsNetPnlUsdt,
        BigDecimal openPositionsCurrentPnlUsdt,
        BigDecimal totalPnlUsdt,
        BigDecimal sessionStartCapital,
        BigDecimal marginPerPosition,
        int leverage,
        BigDecimal positionNotional,
        LaplaceRuntimeState runtimeState,
        Instant cooldownUntil
) {
}
