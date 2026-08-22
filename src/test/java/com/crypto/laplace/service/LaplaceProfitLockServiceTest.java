package com.crypto.laplace.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplaceExecutionPriceProvider;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.persistence.LaplaceTradingSessionEntity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplaceProfitLockServiceTest {
    private LaplaceRuntimeService runtime;
    private LaplacePaperPositionRepository positions;
    private BinanceFuturesClient client;
    private LaplacePaperExecutionService execution;
    private LaplaceTemporaryStateResetService reset;
    private LaplaceProfitLockService service;
    private LaplaceTradingSessionEntity session;

    @BeforeEach
    void setUp() {
        runtime = mock(LaplaceRuntimeService.class); positions = mock(LaplacePaperPositionRepository.class);
        client = mock(BinanceFuturesClient.class); execution = mock(LaplacePaperExecutionService.class);
        reset = mock(LaplaceTemporaryStateResetService.class);
        session = LaplaceTradingSessionEntity.builder().sessionId("session-1").runtimeState(LaplaceRuntimeState.ACTIVE)
                .sessionStartCapital(bd("350")).marginPerPosition(bd("5")).leverage(15)
                .profitTargetPct(bd("5")).minimumLockedProfitPct(bd("4.7")).build();
        when(runtime.isActive()).thenReturn(true); when(runtime.current()).thenReturn(session);
        when(runtime.target(session)).thenReturn(bd("17.5")); when(runtime.minimumLocked(session)).thenReturn(bd("16.45"));
        service = new LaplaceProfitLockService(runtime, positions, client, new LaplacePnlCalculator(), execution,
                reset, Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void profitBelowFivePercentDoesNotStartLiquidation() {
        stubOpen(bd("116"), bd("117"), bd("1"));
        service.evaluate();
        verify(runtime, never()).beginLiquidation(any(), any(), any(), any());
        verify(execution, never()).closeForProfitLock(any(), any(), any());
    }

    @Test
    void profitAboveTargetButBelowFeeAdjustedMinimumDoesNotClose() {
        LaplacePaperPositionEntity position = open(PositionSide.LONG, bd("100"), bd("1"));
        position.setEntryFeeRate(bd("0.02"));
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN)).thenReturn(List.of(position));
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(client.getAllBookTickers()).thenReturn(List.of(ticker(bd("118"), bd("119"))));
        service.evaluate();
        verify(runtime, never()).beginLiquidation(any(), any(), any(), any());
    }

    @Test
    void feeBeforeTargetButFeeAfterBelowTargetDoesNotLiquidate() {
        LaplacePaperPositionEntity position = open(PositionSide.LONG, bd("100"), bd("1"));
        position.setEntryFee(bd("0.20"));
        position.setEntryFeeRate(bd("0.01"));
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN)).thenReturn(List.of(position));
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(client.getAllBookTickers()).thenReturn(List.of(ticker(bd("117.5"), bd("117.6"))));

        service.evaluate();

        verify(runtime, never()).beginLiquidation(any(), any(), any(), any());
    }

    @Test
    void closedOnlyProfitAtFivePercentTransitionsThroughLiquidationAndCooldown() {
        LaplacePaperPositionEntity closed = LaplacePaperPositionEntity.builder().id("closed")
                .sessionId("session-1").status(LaplacePositionStatus.CLOSED).netPnl(bd("17.5"))
                .exitTime(Instant.parse("2026-08-14T11:00:00Z")).build();
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED)).thenReturn(List.of(closed));
        when(runtime.beginLiquidation(any(), any(), any(), eq(bd("17.5")))).thenReturn(true);

        service.evaluate();

        verifyNoInteractions(client);
        verify(runtime).recordLiquidationResult(bd("17.5"));
        verify(runtime).startCooldown(Instant.parse("2026-08-14T11:00:00Z"));
        verify(reset).clearInvertedTrue();
    }

    @Test
    void manualCloseBelowTargetUsesTheSameLiquidationCompoundingAndCooldownPath() {
        var open = open(PositionSide.LONG, bd("100"), bd("1"));
        var closed = closed("session-1", "0.9296");
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED))
                .thenReturn(List.of()).thenReturn(List.of(closed));
        when(client.getAllBookTickers()).thenReturn(List.of(ticker(bd("101"), bd("102"))));
        when(runtime.beginLiquidation(any(), any(), any(), any())).thenReturn(true);

        service.closeCurrentSession();

        verify(runtime).beginLiquidation(eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(bd("0.97")) == 0),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(bd("0.0404")) == 0),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(bd("0.9296")) == 0));
        verify(execution).closeForProfitLock(eq("id"),
                org.mockito.ArgumentMatchers.argThat(q -> ((LaplaceExecutionPriceProvider.Price) q).value().compareTo(bd("101")) == 0), any());
        verify(runtime).recordLiquidationResult(bd("0.9296"));
        verify(runtime).startCooldown(closed.getExitTime());
        verify(reset).clearInvertedTrue();
        verify(reset).clearSharedIfUnused();
    }

    @Test
    void secondSessionProfitOneMillionthBelowItsOwnTargetDoesNotLock() {
        useSecondSession();
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.CLOSED))
                .thenReturn(List.of(closed("session-2", "18.374999")));

        service.evaluate();

        verify(runtime, never()).beginLiquidation(any(), any(), any(), any());
        verifyNoInteractions(client);
    }

    @Test
    void secondSessionProfitExactlyAtItsOwnTargetLocks() {
        useSecondSession();
        var current = closed("session-2", "18.375");
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.CLOSED)).thenReturn(List.of(current));
        when(runtime.beginLiquidation(any(), any(), any(), eq(bd("18.375")))).thenReturn(true);

        service.evaluate();

        verify(runtime).beginLiquidation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("18.375"));
        verify(runtime).recordLiquidationResult(bd("18.375"));
        verifyNoInteractions(client);
    }

    @Test
    void previousSessionProfitIsNotIncludedInCurrentSessionThreshold() {
        useSecondSession();
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-2", LaplacePositionStatus.CLOSED))
                .thenReturn(List.of(closed("session-2", "1")));

        service.evaluate();

        verify(positions, never()).findBySessionIdAndStatus(eq("session-1"), any());
        verify(runtime, never()).beginLiquidation(any(), any(), any(), any());
    }

    @Test
    void bothThresholdsCloseLongAtBidAndShortAtAsk() {
        var longPosition = open(PositionSide.LONG, bd("100"), bd("1")); longPosition.setId("long");
        var shortPosition = open(PositionSide.SHORT, bd("100"), bd("1")); shortPosition.setId("short");
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN))
                .thenReturn(List.of(longPosition, shortPosition)).thenReturn(List.of());
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(client.getAllBookTickers()).thenReturn(List.of(ticker(bd("110"), bd("90"))));
        when(runtime.beginLiquidation(any(), any(), any(), any())).thenReturn(true);
        service.evaluate();
        verify(execution).closeForProfitLock(eq("long"),
                org.mockito.ArgumentMatchers.argThat(q -> ((LaplaceExecutionPriceProvider.Price) q).value().compareTo(bd("110")) == 0), any());
        verify(execution).closeForProfitLock(eq("short"),
                org.mockito.ArgumentMatchers.argThat(q -> ((LaplaceExecutionPriceProvider.Price) q).value().compareTo(bd("90")) == 0), any());
        verify(reset).clearInvertedTrue();
    }

    private void stubOpen(BigDecimal bid, BigDecimal ask, BigDecimal quantity) {
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open(PositionSide.LONG, bd("100"), quantity)));
        when(positions.findBySessionIdAndStatus("session-1", LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(client.getAllBookTickers()).thenReturn(List.of(ticker(bid, ask)));
    }

    private LaplacePaperPositionEntity open(PositionSide side, BigDecimal entry, BigDecimal quantity) {
        return LaplacePaperPositionEntity.builder().id("id").sessionId("session-1").symbol("BTCUSDT")
                .side(side).status(LaplacePositionStatus.OPEN).entryExecutionPrice(entry).quantity(quantity)
                .notional(entry.multiply(quantity)).entryFee(bd("0.03")).entryFeeRate(bd("0.0004")).build();
    }

    private void useSecondSession() {
        session = LaplaceTradingSessionEntity.builder().sessionId("session-2").runtimeState(LaplaceRuntimeState.ACTIVE)
                .sessionStartCapital(bd("367.50")).marginPerPosition(bd("5.25")).leverage(15)
                .profitTargetPct(bd("5")).minimumLockedProfitPct(bd("4.7")).build();
        when(runtime.current()).thenReturn(session);
        when(runtime.target(session)).thenReturn(bd("18.375"));
    }

    private LaplacePaperPositionEntity closed(String sessionId, String netPnl) {
        return LaplacePaperPositionEntity.builder().id("closed-" + sessionId).sessionId(sessionId)
                .status(LaplacePositionStatus.CLOSED).netPnl(bd(netPnl))
                .exitTime(Instant.parse("2026-08-14T11:00:00Z")).build();
    }

    private BookTicker ticker(BigDecimal bid, BigDecimal ask) {
        return BookTicker.builder().symbol("BTCUSDT").bidPrice(bid).askPrice(ask).build();
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
