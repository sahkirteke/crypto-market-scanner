package com.crypto.laplace.pool;
import java.math.BigDecimal;
public final class DailyMarginCalculator { private DailyMarginCalculator(){} public static BigDecimal margin(BigDecimal realizedNetPnl){return new BigDecimal("5").multiply(new BigDecimal("250").add(realizedNetPnl)).divide(new BigDecimal("250"));} }
