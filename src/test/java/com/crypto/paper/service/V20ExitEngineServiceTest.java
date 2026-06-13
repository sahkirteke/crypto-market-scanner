package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.log.V20PaperJsonlLogService;
import com.crypto.paper.model.KlineCandle;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class V20ExitEngineServiceTest {
    private final ScannerProperties properties = new ScannerProperties();
    private final ExitEngineService service = new ExitEngineService(null, null, properties, new V20PnlCalculator(properties));

    @Test
    void longTakeProfitClosesAtPlusTwoPercent() {
        PaperPositionEntity p = longPosition();
        service.evaluatePositionWithCandle(p, candle("103", "100", 1), "5m");
        assertClosed(p, "TAKE_PROFIT", "102.000000000000");
    }

    @Test
    void longStopLossClosesAtMinusOnePointFourPercent() {
        PaperPositionEntity p = longPosition();
        service.evaluatePositionWithCandle(p, candle("101", "98", 1), "5m");
        assertClosed(p, "STOP_LOSS", "98.600000000000");
    }

    @Test
    void shortTakeProfitClosesAtMinusTwoPercent() {
        PaperPositionEntity p = shortPosition();
        service.evaluatePositionWithCandle(p, candle("100", "97", 1), "5m");
        assertClosed(p, "TAKE_PROFIT", "98.000000000000");
    }

    @Test
    void shortStopLossClosesAtPlusOnePointFourPercent() {
        PaperPositionEntity p = shortPosition();
        service.evaluatePositionWithCandle(p, candle("102", "99", 1), "5m");
        assertClosed(p, "STOP_LOSS", "101.400000000000");
    }

    @Test
    void stopFirstWinsWhenSameCandleTouchesTakeProfitAndStopLoss() {
        PaperPositionEntity p = longPosition();
        service.evaluatePositionWithCandle(p, candle("103", "98", 1), "5m");
        assertClosed(p, "STOP_LOSS", "98.600000000000");
    }

    @Test
    void sameFiveMinuteCandleIsNotProcessedTwice() {
        PaperPositionEntity p = longPosition();
        KlineCandle candle = candle("101", "99", 1);
        service.evaluatePositionWithCandle(p, candle, "5m");
        assertThat(p.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        service.evaluatePositionWithCandle(p, candle("103", "98", 1), "5m");
        assertThat(p.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
    }

    @Test
    void trailingPartialAndTimeExitRemainDisabledForV20() {
        PaperPositionEntity p = longPosition();
        p.setTp1(new BigDecimal("101"));
        p.setTp2(new BigDecimal("102"));
        p.setCurrentStop(new BigDecimal("99"));
        p.setMinutesHeld(99999);
        service.evaluatePositionWithCandle(p, candle("101.5", "100.5", 1), "1h");
        assertThat(p.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(p.getTp1Hit()).isFalse();
        assertThat(p.getTp2Hit()).isFalse();
        assertThat(p.getTrailingActive()).isFalse();
        assertThat(p.getExitReason()).isNull();
    }

    @Test
    void closedPositionHasPnlAndFees() {
        PaperPositionEntity p = longPosition();
        service.evaluatePositionWithCandle(p, candle("103", "100", 1), "5m");
        assertThat(p.getGrossPnlUsdt()).isNotNull();
        assertThat(p.getEntryFeeUsdt()).isNotNull();
        assertThat(p.getExitFeeUsdt()).isNotNull();
        assertThat(p.getTotalFeeUsdt()).isNotNull();
        assertThat(p.getNetPnlUsdt()).isNotNull();
        assertThat(p.getRawRealizedPnlPct()).isNotNull();
        assertThat(p.getNetRealizedPnlPct()).isNotNull();
        assertThat(p.getLeveragedNetRealizedPnlPct()).isNotNull();
    }


    @Test
    void longFiveXTakeProfitPnlUsesMarginBasedLeveragedPctAndSeparateFees() {
        PaperPositionEntity p = longPosition();
        service.evaluatePositionWithCandle(p, candle("103", "100", 1), "5m");
        assertThat(p.getUnleveragedTotalFeeUsdt()).isLessThan(p.getLeveragedTotalFeeUsdt());
        assertThat(p.getLeveragedTotalFeeUsdt()).isEqualByComparingTo(p.getUnleveragedTotalFeeUsdt().multiply(new BigDecimal("5")));
        assertThat(p.getLeveragedNetPnlPct()).isEqualByComparingTo(p.getLeveragedNetPnlUsdt().divide(new BigDecimal("100"), 12, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(8, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void shortFiveXStopLossPnlUsesShortFormula() {
        PaperPositionEntity p = shortPosition();
        service.evaluatePositionWithCandle(p, candle("102", "99", 1), "5m");
        assertThat(p.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(p.getLeveragedRawPnlUsdt()).isNegative();
        assertThat(p.getLeveragedNetPnlUsdt()).isNegative();
    }


    @Test
    void jsonlClosedEventContainsMakerFeeAndBothPnlModels() {
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        when(v20Log.formatTr(any())).thenReturn("2026-06-13T18:42:10.125+03:00");
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        PaperPositionEntity p = longPosition();

        service.evaluatePositionWithCandle(p, candle("103", "100", 1), "5m");

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(v20Log).log(captor.capture());
        assertThat(captor.getValue()).containsEntry("eventType", "POSITION_CLOSED");
        assertThat(captor.getValue()).containsEntry("feeMode", "MAKER");
        assertThat(captor.getValue()).containsEntry("feeRate", new BigDecimal("0.0002"));
        assertThat(captor.getValue()).containsEntry("slippagePct", BigDecimal.ZERO);
        assertThat(captor.getValue()).containsKeys("unleveragedNetPnlUsdt", "leveragedNetPnlUsdt", "leveragedNetPnlPct");
    }

    private void assertClosed(PaperPositionEntity p, String reason, String exitPrice) {
        assertThat(p.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(p.getExitReason()).isEqualTo(reason);
        assertThat(p.getExitPrice()).isEqualByComparingTo(exitPrice);
        assertThat(p.getRemainingPositionPct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private PaperPositionEntity longPosition() { return position(PositionSide.LONG, "102.000000000000", "98.600000000000"); }
    private PaperPositionEntity shortPosition() { return position(PositionSide.SHORT, "98.000000000000", "101.400000000000"); }
    private PaperPositionEntity position(PositionSide side, String tp, String sl) {
        return PaperPositionEntity.builder().strategyVersion("V20").symbol("TESTUSDT").side(side).status(PaperPositionStatus.OPEN)
                .entryPrice(new BigDecimal("100")).quantity(new BigDecimal("5")).marginUsdt(new BigDecimal("100")).unleveragedNotionalUsdt(new BigDecimal("100")).leveragedNotionalUsdt(new BigDecimal("500")).notionalUsdt(new BigDecimal("500"))
                .takeProfitPrice(new BigDecimal(tp)).stopLossPrice(new BigDecimal(sl)).openedAt(Instant.EPOCH)
                .tp1Hit(false).tp2Hit(false).trailingActive(false).remainingPositionPct(new BigDecimal("100")).build();
    }
    private KlineCandle candle(String high, String low, long minute) {
        return KlineCandle.builder().openTime(Instant.EPOCH.plusSeconds((minute - 1) * 300)).closeTime(Instant.EPOCH.plusSeconds(minute * 300))
                .high(new BigDecimal(high)).low(new BigDecimal(low)).close(new BigDecimal("100")).interval("5m").build();
    }
}
