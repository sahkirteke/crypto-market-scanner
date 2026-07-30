package com.crypto.laplace.execution;

import java.util.List;

public record LaplaceEntryDecision(boolean entryAllowed, boolean riskyEntry,
                                   boolean strongConfirmedSignal,
                                   List<LaplaceEntrySkipReason> skipReasons) {
    public LaplaceEntryDecision {
        skipReasons = List.copyOf(skipReasons);
    }
}
