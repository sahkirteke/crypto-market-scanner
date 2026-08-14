package com.crypto.api.dto;
import com.crypto.common.enums.PositionSide;import java.math.BigDecimal;import java.time.Instant;
public record LaplaceClosedPaperPositionResponse(String positionId,String symbol,PositionSide side,Instant entryTime,Instant exitTime,BigDecimal netPnl,String exitReason){}
