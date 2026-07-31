package com.crypto.laplace.execution;

import java.util.List;

import com.crypto.common.enums.PositionSide;

public record LaplaceEntryDecision(boolean entryAllowed, boolean riskyEntry, boolean strongConfirmedSignal,
                                   boolean rawLongRule, boolean rawShortRule, boolean signalInverted,
                                   PositionSide effectiveExecutionSide,
                                   List<LaplaceEntrySkipReason> skipReasons) {
    public LaplaceEntryDecision {
        skipReasons = List.copyOf(skipReasons);
    }
}
