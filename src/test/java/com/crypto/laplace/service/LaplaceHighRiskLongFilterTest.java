package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceHighRiskLongFilterTest {
    private final LaplaceHighRiskLongFilter filter = new LaplaceHighRiskLongFilter(org.mockito.Mockito.mock(BinanceFuturesClient.class));

    @Test void highVolatilityLongAfterDropIsSkipped() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(3.0, 1.8)).highRiskLong()).isTrue(); }
    @Test void atrBelowThresholdDoesNotSkipLong() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(2.49, 2.49)).highRiskLong()).isFalse(); }
    @Test void insufficientDropDoesNotSkipLong() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(3.0, 1.2)).highRiskLong()).isFalse(); }
    @Test void inclusiveThresholdsSkipLong() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(2.5, 1.25)).highRiskLong()).isTrue(); }
    @Test void shortIsNeverBlocked() { assertThat(filter.evaluateCompletedCandles(PositionSide.SHORT, candles(5.0, 10.0)).highRiskLong()).isFalse(); }
    @Test void twoHourRiseDoesNotSkipLong() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(3.0, -2.0)).highRiskLong()).isFalse(); }
    @Test void insufficientCandlesDoNotBlockLong() { assertThat(filter.evaluateCompletedCandles(PositionSide.LONG, candles(3.0, 1.8).subList(0, 14)).highRiskLong()).isFalse(); }

    private List<Kline> candles(double atr, double dropPct) {
        List<Kline> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        double lastClose = 100.0;
        double referenceClose = lastClose / (1.0 - dropPct / 100.0);
        for (int index = 0; index < 15; index++) {
            double close = index == 10 ? referenceClose : lastClose;
            double previousClose = index == 0 ? close : candles.get(index - 1).getClose().doubleValue();
            candles.add(Kline.builder().symbol("BTCUSDT").interval("30m")
                    .openTime(start.plusSeconds(index * 1800L)).closeTime(start.plusSeconds((index + 1L) * 1800L))
                    .high(BigDecimal.valueOf(previousClose + atr / 2.0)).low(BigDecimal.valueOf(previousClose - atr / 2.0))
                    .close(BigDecimal.valueOf(close)).closed(true).build());
        }
        return candles;
    }
}
