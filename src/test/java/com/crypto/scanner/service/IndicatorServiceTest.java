package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IndicatorServiceTest {
    private final IndicatorService indicatorService = new IndicatorService();

    @Test
    void calculateReturnsTechnicalSnapshotWhenEnoughClosedKlinesExist() {
        List<Kline> klines = closedKlines(230);

        TechnicalSnapshot snapshot = indicatorService.calculate("TESTUSDT", "1h", klines);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSymbol()).isEqualTo("TESTUSDT");
        assertThat(snapshot.getInterval()).isEqualTo("1h");
        assertThat(snapshot.getClose()).isNotNull();
        assertThat(snapshot.getEma20()).isNotNull();
        assertThat(snapshot.getEma50()).isNotNull();
        assertThat(snapshot.getEma200()).isNotNull();
        assertThat(snapshot.getRsi14()).isNotNull();
        assertThat(snapshot.getMacdHist()).isNotNull();
        assertThat(snapshot.getAtr14()).isNotNull();
        assertThat(snapshot.getVolumeSma20()).isNotNull();
        assertThat(snapshot.getVolumeRatio()).isNotNull();
    }

    @Test
    void calculateThrowsAndCalculatePairReturnsNotReadyWhenClosedKlinesAreInsufficient() {
        List<Kline> klines = closedKlines(100);

        assertThatThrownBy(() -> indicatorService.calculate("TESTUSDT", "1h", klines))
                .isInstanceOf(IllegalArgumentException.class);

        TechnicalSnapshotPair pair = indicatorService.calculatePair(KlineBundle.builder()
                .symbol("TESTUSDT")
                .oneHourKlines(klines)
                .fourHourKlines(closedKlines(230))
                .build());

        assertThat(pair.getReady()).isFalse();
        assertThat(pair.getReasons()).contains(ReasonTag.DATA_NOT_READY);
    }

    @Test
    void calculateIgnoresUnclosedKline() {
        List<Kline> klines = new ArrayList<>(closedKlines(230));
        BigDecimal lastClosedClose = klines.get(klines.size() - 1).getClose();
        klines.add(kline(230, new BigDecimal("99999"), new BigDecimal("10"), false));

        TechnicalSnapshot snapshot = indicatorService.calculate("TESTUSDT", "1h", klines);

        assertThat(snapshot.getClose()).isEqualByComparingTo(lastClosedClose);
        assertThat(snapshot.getClose()).isNotEqualByComparingTo(new BigDecimal("99999"));
    }

    @Test
    void calculatePopulatesPreviousCandleValues() {
        List<Kline> klines = new ArrayList<>(closedKlines(230));
        klines.set(228, kline(228, new BigDecimal("123.45"), new BigDecimal("130.45"),
                new BigDecimal("120.45"), new BigDecimal("10"), true));
        klines.set(229, kline(229, new BigDecimal("140.45"), new BigDecimal("145.45"),
                new BigDecimal("139.45"), new BigDecimal("10"), true));

        TechnicalSnapshot snapshot = indicatorService.calculate("TESTUSDT", "1h", klines);

        assertThat(snapshot.getPreviousClose()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(snapshot.getPreviousHigh()).isEqualByComparingTo(new BigDecimal("130.45"));
        assertThat(snapshot.getPreviousLow()).isEqualByComparingTo(new BigDecimal("120.45"));
    }

    @Test
    void calculateDoesNotThrowWhenVolumeSma20IsZero() {
        List<Kline> klines = IntStream.range(0, 230)
                .mapToObj(index -> kline(index, BigDecimal.valueOf(100L + index), BigDecimal.ZERO, true))
                .toList();

        assertThatNoException().isThrownBy(() -> {
            TechnicalSnapshot snapshot = indicatorService.calculate("TESTUSDT", "1h", klines);
            assertThat(snapshot.getVolumeSma20()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(snapshot.getVolumeRatio()).isNull();
        });
    }

    private List<Kline> closedKlines(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> kline(index, BigDecimal.valueOf(100L + index), new BigDecimal("10"), true))
                .toList();
    }

    private Kline kline(int index, BigDecimal close, BigDecimal volume, Boolean closed) {
        return kline(index, close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), volume, closed);
    }

    private Kline kline(int index, BigDecimal close, BigDecimal high, BigDecimal low, BigDecimal volume, Boolean closed) {
        Instant openTime = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 3600L);
        return Kline.builder()
                .symbol("TESTUSDT")
                .interval("1h")
                .openTime(openTime)
                .open(close.subtract(new BigDecimal("0.50")))
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .closeTime(openTime.plusSeconds(3599))
                .closed(closed)
                .build();
    }
}
