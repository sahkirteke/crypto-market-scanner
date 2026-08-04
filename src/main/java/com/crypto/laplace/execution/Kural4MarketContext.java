package com.crypto.laplace.execution;

import java.time.Instant;

public record Kural4MarketContext(Instant signalTime, Instant lastCompleted5mCloseTime,
        double btcLastClose, double btcReturn15mPct, double btcReturn30mPct,
        double btcRangePosition60, int completedCandleCount) { }
