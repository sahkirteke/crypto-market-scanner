package com.crypto.laplace.api;

import com.crypto.laplace.model.LaplaceRuntimeState;
import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceRuntimeStatusResponse(
        LaplaceRuntimeState runtimeState,
        String sessionId,
        BigDecimal sessionStartCapital,
        BigDecimal marginPerPosition,
        int leverage,
        BigDecimal positionNotional,
        BigDecimal profitTargetPct,
        BigDecimal profitTargetUsdt,
        BigDecimal minimumLockedProfitPct,
        BigDecimal minimumLockedProfitUsdt,
        Instant cooldownUntil
) {
}
