package com.crypto.laplace.model;

import java.time.Instant;

/** Immutable, candle-aligned view of the active Laplace coin pool. */
public record LaplaceMarketBreadthSnapshot(
        Instant referenceTime,
        double marketBreadth2h,
        double marketBreadth4h,
        int positiveCoinCount2h,
        int validCoinCount2h,
        int positiveCoinCount4h,
        int validCoinCount4h) {
    public boolean available() {
        return validCoinCount2h > 0 && validCoinCount4h > 0;
    }
}
