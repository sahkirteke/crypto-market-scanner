package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import java.time.Instant;
import java.math.BigDecimal;
import com.crypto.laplace.execution.LaplaceEntrySkipReason;
import java.util.List;

public record LaplaceSkippedEntrySummary(String symbol, Instant skipTime, PositionSide effectiveExecutionSide,
                                         BigDecimal entryPrice, List<LaplaceEntrySkipReason> skipReasons) {
    public LaplaceSkippedEntrySummary { skipReasons = List.copyOf(skipReasons); }
}
