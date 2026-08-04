package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;

public record LaplaceExecutionOverlayContext(
        PositionSide rawExecutionSide,
        PositionSide volumeProfileFilteringSide,
        Kural4ExecutionDecision kural4Decision,
        boolean legacySignalInverted) {
}
