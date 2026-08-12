package com.crypto.laplace.model;

import java.time.Instant;

public record PositionCatchUpResult(int processedCandles, boolean positionClosed, Instant lastProcessedCloseTime) {}
