package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import java.time.Instant;
import java.math.BigDecimal;

public record LaplaceSkippedEntrySummary(String symbol, Instant skipTime, PositionSide effectiveExecutionSide,
                                         BigDecimal entryPrice) {
}
