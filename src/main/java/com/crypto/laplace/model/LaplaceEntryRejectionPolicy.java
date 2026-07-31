package com.crypto.laplace.model;

public enum LaplaceEntryRejectionPolicy {
    PERSIST_UNTIL_OPPOSITE_RAW_SIGNAL,
    RETRY_NEXT_CLOSED_30M_BAR
}
