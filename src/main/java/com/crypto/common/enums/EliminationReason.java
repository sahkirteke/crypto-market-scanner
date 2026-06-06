package com.crypto.common.enums;

public enum EliminationReason {
    LOW_VOLUME,
    HIGH_SPREAD,
    MISSING_TICKER,
    MISSING_BOOK_TICKER,
    DATA_NOT_READY,
    BLACKLIST,
    MARKET_PANIC_NO_NEW_ENTRY,
    INVALID_QUANTITY,
    DATA_ERROR,
    SCORE_BELOW_THRESHOLD,
    NONE
}
