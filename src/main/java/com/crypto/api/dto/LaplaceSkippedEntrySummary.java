package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import java.time.Instant;

public record LaplaceSkippedEntrySummary(String symbol, Instant skipTime, PositionSide effectiveExecutionSide) {
}
