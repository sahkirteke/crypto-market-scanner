package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import java.util.List;

public record Kural4ExecutionDecision(boolean entryAllowed, PositionSide rawExecutionSide,
        PositionSide finalExecutionSide, Kural4ExecutionAction action,
        List<Kural4DecisionReason> reasons, Kural4MarketContext marketContext) {
    public Kural4ExecutionDecision { reasons = List.copyOf(reasons); }
}
