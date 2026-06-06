package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketBreadthServiceTest {
    private final MarketBreadthService marketBreadthService = new MarketBreadthService();

    @Test
    void calculateMarketBreadthPctReturnsPercentageOfReadyPairsAboveFourHourEma20() {
        List<TechnicalSnapshotPair> pairs = List.of(
                pair("ONEUSDT", "110", "100"),
                pair("TWOUSDT", "120", "100"),
                pair("THREEUSDT", "130", "100"),
                pair("FOURUSDT", "90", "100"));

        BigDecimal marketBreadthPct = marketBreadthService.calculateMarketBreadthPct(pairs);

        assertThat(marketBreadthPct).isEqualByComparingTo(new BigDecimal("75"));
    }

    private TechnicalSnapshotPair pair(String symbol, String close, String ema20) {
        return TechnicalSnapshotPair.builder()
                .symbol(symbol)
                .ready(true)
                .fourHour(TechnicalSnapshot.builder()
                        .symbol(symbol)
                        .close(new BigDecimal(close))
                        .ema20(new BigDecimal(ema20))
                        .build())
                .build();
    }
}
