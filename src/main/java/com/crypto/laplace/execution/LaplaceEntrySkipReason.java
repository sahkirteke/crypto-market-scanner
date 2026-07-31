package com.crypto.laplace.execution;

public enum LaplaceEntrySkipReason {
    RISKY_ENTRY,
    WEAK_CURRENT_SLOPE,
    WEAK_PREVIOUS_SLOPE,
    WEAK_BOTH_SLOPES
}
