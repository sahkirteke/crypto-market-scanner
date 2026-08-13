package com.crypto.laplace.session;

import java.math.*;

public final class LaplaceSessionMath {
    private LaplaceSessionMath() {}
    public static BigDecimal returnRatio(BigDecimal pnl, BigDecimal capital) {
        return pnl.divide(capital, 12, RoundingMode.HALF_UP);
    }
    public static BigDecimal nextMargin(BigDecimal margin, BigDecimal sessionReturn, boolean compound) {
        if (!compound || sessionReturn.signum() <= 0) return margin.setScale(2, RoundingMode.HALF_UP);
        return margin.multiply(BigDecimal.ONE.add(sessionReturn)).setScale(2, RoundingMode.HALF_UP);
    }
    public static boolean shouldProfitLock(BigDecimal capital, BigDecimal realized, BigDecimal projected,
                                           BigDecimal realizedTarget, BigDecimal projectedFloor) {
        return realized.compareTo(capital.multiply(realizedTarget)) >= 0
                && projected.compareTo(capital.multiply(projectedFloor)) >= 0;
    }
}
