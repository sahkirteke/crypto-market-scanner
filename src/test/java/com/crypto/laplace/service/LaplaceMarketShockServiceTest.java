package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplaceMarketShockDirection;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplaceMarketShockServiceTest {
    private final StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
    private final LaplaceMarketShockDataService data = mock(LaplaceMarketShockDataService.class);
    private final LaplacePaperPositionRepository positions = mock(LaplacePaperPositionRepository.class);
    private final LaplacePaperExecutionService execution = mock(LaplacePaperExecutionService.class);
    private LaplaceMarketShockService service;
    private Map<String, List<Kline>> current;
    private final Set<String> symbols = new LinkedHashSet<>(List.of(
            "BTCUSDT", "ETHUSDT", "A", "B", "C", "D", "E", "F", "G", "H"));
    private final Instant candidateTime = Instant.parse("2026-08-20T12:40:00Z");

    @BeforeEach void setUp() {
        service = new LaplaceMarketShockService(universe, data, positions, execution);
        when(universe.symbols()).thenReturn(symbols);
        when(data.loadClosed(anyString())).thenAnswer(invocation -> current.getOrDefault(invocation.getArgument(0), List.of()));
        when(positions.findByStrategyAndStatus(anyString(), eq(LaplacePositionStatus.OPEN))).thenReturn(List.of());
    }

    @Test void upBreadthAndBtcEthCreateCandidateWithoutClosing() {
        current = market(candidateTime, 8, true, false);
        service.evaluate();
        assertThat(service.pendingCandidate().direction()).isEqualTo(LaplaceMarketShockDirection.UP);
        verifyNoInteractions(execution);
    }

    @Test void downBreadthAndBtcEthCreateCandidate() {
        current = market(candidateTime, 8, false, false);
        service.evaluate();
        assertThat(service.pendingCandidate().direction()).isEqualTo(LaplaceMarketShockDirection.DOWN);
    }

    @Test void belowSeventyFiveOrWrongConfirmationCoinCreatesNoCandidate() {
        current = market(candidateTime, 7, true, false);
        service.evaluate();
        assertThat(service.pendingCandidate()).isNull();
        current = market(candidateTime, 8, true, true);
        service.evaluate();
        assertThat(service.pendingCandidate()).isNull();
    }

    @Test void incompleteLatestCandleIsIgnored() {
        current = market(candidateTime, 8, true, false);
        current.replaceAll((symbol, candles) -> {
            List<Kline> copy = new ArrayList<>(candles);
            copy.add(candle(symbol, candidateTime.plusSeconds(300), "200", false));
            return copy;
        });
        service.evaluate();
        assertThat(service.pendingCandidate().closeTime()).isEqualTo(candidateTime);
    }

    @Test void continuationBelowSixtyIsTransientAndDoesNotClose() {
        current = market(candidateTime, 8, true, false); service.evaluate();
        current = confirmation(current, 5, true, candidateTime.plusSeconds(300)); service.evaluate();
        assertThat(service.pendingCandidate()).isNull();
        verifyNoInteractions(execution);
    }

    @Test void exactlySixtyWithTenOpenAndHalfOppositeClosesOnlyShorts() {
        current = market(candidateTime, 8, true, false); service.evaluate();
        List<LaplacePaperPositionEntity> open = positions(5, 5);
        when(positions.findByStrategyAndStatus(anyString(), eq(LaplacePositionStatus.OPEN))).thenReturn(open);
        when(execution.closeForPersistentMarketShock(anyString(), any())).thenReturn(true);
        current = confirmation(current, 6, true, candidateTime.plusSeconds(300)); service.evaluate();
        verify(execution, times(5)).closeForPersistentMarketShock(argThat(id -> id.startsWith("S")), any());
        verify(execution, never()).closeForPersistentMarketShock(argThat(id -> id.startsWith("L")), any());
    }

    @Test void persistentWithNineOpenOrLessThanHalfOppositeDoesNotClose() {
        current = market(candidateTime, 8, true, false); service.evaluate();
        when(positions.findByStrategyAndStatus(anyString(), eq(LaplacePositionStatus.OPEN))).thenReturn(positions(5, 4));
        current = confirmation(current, 6, true, candidateTime.plusSeconds(300)); service.evaluate();
        verify(execution, never()).closeForPersistentMarketShock(anyString(), any());
        service.clearRuntimeState(); reset(execution);
        current = market(candidateTime.plusSeconds(600), 8, true, false); service.evaluate();
        when(positions.findByStrategyAndStatus(anyString(), eq(LaplacePositionStatus.OPEN))).thenReturn(positions(4, 6));
        current = confirmation(current, 6, true, candidateTime.plusSeconds(900)); service.evaluate();
        verify(execution, never()).closeForPersistentMarketShock(anyString(), any());
    }

    @Test void missedImmediateCandleExpiresCandidateWithoutLateConfirmation() {
        current = market(candidateTime, 8, true, false); service.evaluate();
        current = confirmation(current, 10, true, candidateTime.plusSeconds(600)); service.evaluate();
        verifyNoInteractions(execution);
    }

    @Test void clearRemovesPendingCandidate() {
        current = market(candidateTime, 8, true, false); service.evaluate();
        service.clearRuntimeState();
        assertThat(service.pendingCandidate()).isNull();
    }

    private Map<String, List<Kline>> market(Instant close, int directional, boolean up, boolean wrongEth) {
        Map<String, List<Kline>> result = new LinkedHashMap<>(); int index = 0;
        for (String symbol : symbols) {
            boolean moves = index++ < directional;
            boolean symbolUp = moves == up;
            if (symbol.equals("BTCUSDT")) symbolUp = up;
            if (symbol.equals("ETHUSDT")) symbolUp = wrongEth ? !up : up;
            BigDecimal latest = symbolUp ? new BigDecimal("101") : new BigDecimal("99");
            result.put(symbol, List.of(candle(symbol, close.minusSeconds(900), "100", true),
                    candle(symbol, close.minusSeconds(600), "100", true), candle(symbol, close.minusSeconds(300), "100", true),
                    candle(symbol, close, latest.toPlainString(), true)));
        }
        return result;
    }

    private Map<String, List<Kline>> confirmation(Map<String, List<Kline>> base, int continuing, boolean up, Instant time) {
        Map<String, List<Kline>> result = new LinkedHashMap<>(); int index = 0;
        for (String symbol : symbols) {
            List<Kline> candles = new ArrayList<>(base.get(symbol)); BigDecimal prior = candles.getLast().getClose();
            boolean continues = index++ < continuing;
            BigDecimal next = up == continues ? prior.add(BigDecimal.ONE) : prior.subtract(BigDecimal.ONE);
            candles.add(candle(symbol, time, next.toPlainString(), true)); result.put(symbol, candles);
        }
        return result;
    }

    private List<LaplacePaperPositionEntity> positions(int shorts, int longs) {
        List<LaplacePaperPositionEntity> result = new ArrayList<>();
        for (int i=0;i<shorts;i++) result.add(LaplacePaperPositionEntity.builder().id("S"+i).symbol("S"+i).side(PositionSide.SHORT).status(LaplacePositionStatus.OPEN).build());
        for (int i=0;i<longs;i++) result.add(LaplacePaperPositionEntity.builder().id("L"+i).symbol("L"+i).side(PositionSide.LONG).status(LaplacePositionStatus.OPEN).build());
        return result;
    }

    private Kline candle(String symbol, Instant close, String price, boolean closed) {
        return Kline.builder().symbol(symbol).interval("5m").closeTime(close).close(new BigDecimal(price)).closed(closed).build();
    }
}
