package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.V20SignalResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class V20SignalServiceTest {
    private final V20SignalService service = new V20SignalService(new ScannerProperties());

    @Test
    void longBaseTechnicalSignalPassesAndFailsWithLongThresholds() {
        V20SignalResult pass = service.evaluateLong(long4h(), oneHourLong(), bd("0.00008000"), funding(), true, false);
        assertThat(pass.isBaseSignalPass()).isTrue();
        TechnicalSnapshot fail = long4h(); fail.setDiDiff(bd("-12.1"));
        assertThat(service.evaluateLong(fail, oneHourLong(), bd("0.00008000"), funding(), true, false).isBaseSignalPass()).isFalse();
    }

    @Test
    void shortBaseTechnicalSignalPassesAndFailsWithShortThresholds() {
        V20SignalResult pass = service.evaluateShort(short4h(), oneHourShort(), bd("0.00008000"), funding(), true, false);
        assertThat(pass.isBaseSignalPass()).isTrue();
        TechnicalSnapshot fail = short4h(); fail.setTakerBuyRatio(bd("0.531"));
        assertThat(service.evaluateShort(fail, oneHourShort(), bd("0.00008000"), funding(), true, false).isBaseSignalPass()).isFalse();
    }

    @Test
    void longSignalScoreMeetsMinimum() {
        V20SignalResult result = service.evaluateLong(long4h(), oneHourLong(), bd("0.00008000"), funding(), true, false);
        assertThat(result.getSignalScore()).isGreaterThanOrEqualTo(9);
        assertThat(result.getReasons()).contains("4H_TAKER_BUY_GE_0_50", "QUALITY_PASS");
    }

    @Test
    void shortSignalScoreMeetsMinimum() {
        V20SignalResult result = service.evaluateShort(short4h(), oneHourShort(), bd("0.00008000"), funding(), true, false);
        assertThat(result.getSignalScore()).isGreaterThanOrEqualTo(9);
        assertThat(result.getReasons()).contains("4H_TAKER_BUY_LE_0_50", "QUALITY_PASS");
    }

    @Test
    void bollingerPositionAddsScoreButDoesNotHardGate() {
        TechnicalSnapshot noBb = long4h(); noBb.setBbPosition(bd("0.90"));
        V20SignalResult result = service.evaluateLong(noBb, oneHourLong(), bd("0.00008000"), funding(), true, false);
        assertThat(result.isBaseSignalPass()).isTrue();
        assertThat(result.getReasons()).doesNotContain("BB_POSITION_IDEAL");
        noBb.setBbPosition(bd("0.50"));
        assertThat(service.evaluateLong(noBb, oneHourLong(), bd("0.00008000"), funding(), true, false).getReasons()).contains("BB_POSITION_IDEAL");
    }

    @Test
    void fundingMa3UsesLastThreeFundingRatesAsBigDecimalAverage() {
        assertThat(service.fundingMa3(List.of(bd("0.00001"), bd("0.00002"), bd("0.00009"), bd("0.00006"), bd("0.000075"))))
                .isEqualByComparingTo(bd("0.000075000000"));
    }


    @Test
    void qualityDefaultsToInsufficientHistoryWhenNoPassOrFailFlagIsProvided() {
        V20SignalResult neutralQuality = service.evaluateLong(long4h(), oneHourLong(), bd("0.00008000"), funding(), false, false);
        V20SignalResult passQuality = service.evaluateLong(long4h(), oneHourLong(), bd("0.00008000"), funding(), true, false);
        V20SignalResult failQuality = service.evaluateLong(long4h(), oneHourLong(), bd("0.00008000"), funding(), false, true);
        assertThat(passQuality.getSignalScore()).isEqualTo(neutralQuality.getSignalScore() + 2);
        assertThat(failQuality.getSignalScore()).isEqualTo(neutralQuality.getSignalScore() - 2);
        assertThat(neutralQuality.getReasons()).doesNotContain("QUALITY_PASS", "QUALITY_FAIL");
    }

    @Test
    void distFromLow20PctIsUsedForLongEntry() {
        TechnicalSnapshot snapshot = long4h(); snapshot.setDistFromLow20Pct(bd("6.1"));
        V20SignalResult result = service.evaluateLong(snapshot, oneHourLong(), bd("0.00008000"), funding(), true, false);
        assertThat(result.isBaseSignalPass()).isTrue();
        assertThat(result.isEntryFiltersPass()).isFalse();
    }

    @Test
    void distFromHigh20PctIsUsedForShortEntry() {
        TechnicalSnapshot snapshot = short4h(); snapshot.setDistFromHigh20Pct(bd("6.1"));
        V20SignalResult result = service.evaluateShort(snapshot, oneHourShort(), bd("0.00008000"), funding(), true, false);
        assertThat(result.isBaseSignalPass()).isTrue();
        assertThat(result.isEntryFiltersPass()).isFalse();
    }

    private TechnicalSnapshot long4h() {
        return TechnicalSnapshot.builder().rsi14(bd("50")).adx14(bd("25")).atrPct(bd("2.0"))
                .ema20Ema50CompPct(bd("0.5")).closeEma20DistPct(bd("1.0")).distFromLow20Pct(bd("3.0"))
                .diDiff(bd("-1")).takerBuyRatio(bd("0.52")).closePosition(bd("0.50")).volumeRatio(bd("1.0"))
                .rangePct(bd("2.0")).bbPosition(bd("0.50")).build();
    }
    private TechnicalSnapshot short4h() {
        return TechnicalSnapshot.builder().rsi14(bd("50")).adx14(bd("25")).atrPct(bd("2.0"))
                .ema20Ema50CompPct(bd("0.5")).closeEma20DistPct(bd("1.0")).distFromHigh20Pct(bd("3.0"))
                .diDiff(bd("1")).takerBuyRatio(bd("0.46")).closePosition(bd("0.50")).volumeRatio(bd("1.0"))
                .rangePct(bd("2.0")).bbPosition(bd("0.50")).build();
    }
    private TechnicalSnapshot oneHourLong() { return TechnicalSnapshot.builder().takerBuyRatio(bd("0.52")).closePosition(bd("0.50")).build(); }
    private TechnicalSnapshot oneHourShort() { return TechnicalSnapshot.builder().takerBuyRatio(bd("0.48")).closePosition(bd("0.50")).build(); }
    private List<BigDecimal> funding() { return List.of(bd("0.00009"), bd("0.00008"), bd("0.000075")); }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
