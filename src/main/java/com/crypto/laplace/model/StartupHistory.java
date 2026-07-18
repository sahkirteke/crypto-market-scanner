package com.crypto.laplace.model;

import com.crypto.domain.model.Kline;
import java.time.Instant;
import java.util.List;

public record StartupHistory(
        String symbol,
        List<Kline> candles,
        List<PreparedLaplaceCandle> preparedCandles,
        Instant baselineCloseTime,
        StartupState state) {
    public StartupHistory {
        candles = List.copyOf(candles);
        preparedCandles = List.copyOf(preparedCandles);
    }
}
