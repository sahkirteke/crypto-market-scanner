package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
import com.crypto.paper.model.PaperExitEvaluationResult;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.IndicatorService;
import java.math.BigDecimal;
import java.net.SocketException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExitEngineServiceTest {

    @Test
    void calculatesLongPnlPct() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        BigDecimal pnlPct = service.calculateUnrealizedPnlPct(position(PositionSide.LONG), new BigDecimal("102"));

        assertThat(pnlPct).isEqualByComparingTo("2.00000000");
    }

    @Test
    void calculatesShortPnlPct() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        BigDecimal pnlPct = service.calculateUnrealizedPnlPct(position(PositionSide.SHORT), new BigDecimal("98"));

        assertThat(pnlPct).isEqualByComparingTo("2.00000000");
    }

    @Test
    void signalInvalidationConditionDoesNotClosePosition() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);

        assertThat(service.shouldSignalInvalidate(
                position,
                new BigDecimal("99"),
                new BigDecimal("100"),
                new BigDecimal("-0.1"),
                MarketRegime.RISK_OFF
        )).isFalse();

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("100.1"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(result.getExitReason()).isNull();
    }

    @Test
    void triggersLongStopLoss() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.LONG), new BigDecimal("99.3"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
    }

    @Test
    void triggersLongTakeProfit() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.LONG), new BigDecimal("101.1"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TAKE_PROFIT");
    }

    @Test
    void triggersShortStopLoss() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.SHORT), new BigDecimal("100.7"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
    }

    @Test
    void triggersShortTakeProfit() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.SHORT), new BigDecimal("98.9"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TAKE_PROFIT");
    }

    @Test
    void timeStopClosesWhenPnlIsNonPositive() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(300L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("99.9"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TIME_STOP");
    }

    @Test
    void timeStopDoesNotClosePositivePnlWhenConfiguredToCloseOnlyNonPositive() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(300L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("100.1"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(result.getExitReason()).isNull();
    }

    @Test
    void lastClosedOneHourCandleBeforePositionOpenDoesNotTriggerSignalInvalidation() {
        PaperPositionRepository repository = mock(PaperPositionRepository.class);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        IndicatorService indicatorService = mock(IndicatorService.class);
        MarketScanRunRepository marketScanRunRepository = mock(MarketScanRunRepository.class);
        ExitEngineService service = service(repository, client);
        ReflectionTestUtils.setField(service, "indicatorService", indicatorService);
        ReflectionTestUtils.setField(service, "marketScanRunRepository", marketScanRunRepository);
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.parse("2026-06-11T16:04:17Z"));
        List<Kline> closedKlines = hourlyKlinesEndingAt(
                Instant.parse("2026-06-11T15:00:00Z"),
                Instant.parse("2026-06-11T15:59:59Z")
        );
        MarketScanRunEntity run = new MarketScanRunEntity();
        run.setMarketRegime(MarketRegime.CHOP);
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(position));
        when(repository.save(position)).thenReturn(position);
        when(client.getKlines(position.getSymbol(), "1h", 250)).thenReturn(closedKlines);
        when(indicatorService.calculateOneHour(position.getSymbol(), closedKlines)).thenReturn(TechnicalSnapshot.builder()
                .close(new BigDecimal("98"))
                .ema20(new BigDecimal("100"))
                .macdHist(new BigDecimal("-0.1"))
                .atr14(new BigDecimal("1"))
                .build());
        when(marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED")).thenReturn(Optional.of(run));

        List<PaperPositionEntity> closed = service.evaluateOpenPositions();

        assertThat(closed).isEmpty();
        assertThat(position.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(position.getExitReason()).isNull();
        assertThat(position.getLastExitCandleCloseTime()).isNull();
    }

    @Test
    void calculatesLongFavorableAndAdverseMoves() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setHighestPrice(new BigDecimal("103"));
        position.setLowestPrice(new BigDecimal("99"));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("102"));

        assertThat(result.getMaxFavorableMovePct()).isEqualByComparingTo("3.00000000");
        assertThat(result.getMaxAdverseMovePct()).isEqualByComparingTo("-1.00000000");
    }

    @Test
    void calculatesShortFavorableAndAdverseMoves() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.SHORT);
        position.setHighestPrice(new BigDecimal("101"));
        position.setLowestPrice(new BigDecimal("97"));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("98"));

        assertThat(result.getMaxFavorableMovePct()).isEqualByComparingTo("3.00000000");
        assertThat(result.getMaxAdverseMovePct()).isEqualByComparingTo("-1.00000000");
    }

    @Test
    void calculatesMinutesHeldAndBarsHeld() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(125L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("100.1"));

        assertThat(result.getMinutesHeld()).isBetween(125, 126);
        assertThat(result.getBarsHeld()).isEqualTo(result.getMinutesHeld() / 60);
    }

    @Test
    void evaluateOpenPositionsDoesNotClosePositionWhenPriceIsMissing() {
        PaperPositionRepository repository = mock(PaperPositionRepository.class);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        PaperPositionEntity position = position(PositionSide.LONG);
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(position));
        when(client.getAll24hTickers()).thenReturn(List.of(Ticker24h.builder()
                .symbol("ETHUSDT")
                .lastPrice(new BigDecimal("200"))
                .build()));
        ExitEngineService service = service(repository, client);

        List<PaperPositionEntity> closed = service.evaluateOpenPositions();

        assertThat(closed).isEmpty();
        assertThat(position.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        verify(repository, never()).save(position);
    }


    @Test
    void intrabarEvaluationSkipsPositionWhenKlinesThrowException() {
        PaperPositionRepository repository = mock(PaperPositionRepository.class);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        PaperPositionEntity position = position(PositionSide.LONG);
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(position));
        when(client.getKlines("BTCUSDT", "5m", 3))
                .thenThrow(new RuntimeException(new SocketException("Connection reset")));
        ExitEngineService service = service(repository, client);

        PaperExitEvaluationResult result = service.evaluateOpenPositionsWithInterval("5m");

        assertThat(result.getSkippedErrorCount()).isEqualTo(1);
        assertThat(result.getCheckedCount()).isZero();
        assertThat(result.getClosedCount()).isZero();
        assertThat(position.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(position.getLastCheckedAt()).isNull();
        assertThat(position.getLastExitCandleCloseTime()).isNull();
        verify(repository, never()).save(position);
    }

    @Test
    void intrabarEvaluationContinuesWithOtherPositionsAfterTransientKlineError() {
        PaperPositionRepository repository = mock(PaperPositionRepository.class);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        PaperPositionEntity btc = position(PositionSide.LONG);
        btc.setSymbol("BTCUSDT");
        PaperPositionEntity eth = position(PositionSide.LONG);
        eth.setId(2L);
        eth.setSymbol("ETHUSDT");
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(btc, eth));
        when(client.getKlines("BTCUSDT", "5m", 3))
                .thenThrow(new RuntimeException(new SocketException("Connection reset")));
        when(client.getKlines("ETHUSDT", "5m", 3)).thenReturn(List.of(kline("ETHUSDT")));
        when(repository.save(eth)).thenReturn(eth);
        ExitEngineService service = service(repository, client);

        PaperExitEvaluationResult result = service.evaluateOpenPositionsWithInterval("5m");

        assertThat(result.getSkippedErrorCount()).isEqualTo(1);
        assertThat(result.getCheckedCount()).isEqualTo(1);
        assertThat(result.getClosedCount()).isZero();
        assertThat(btc.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(btc.getLastCheckedAt()).isNull();
        assertThat(eth.getLastCheckedAt()).isNotNull();
        verify(repository, never()).save(btc);
        verify(repository).save(eth);
    }

    private ExitEngineService service(PaperPositionRepository repository, BinanceFuturesClient client) {
        return new ExitEngineService(repository, client, properties());
    }

    private ScannerProperties properties() {
        ScannerProperties properties = new ScannerProperties();
        ScannerProperties.PaperExit paperExit = new ScannerProperties.PaperExit();
        paperExit.setTakeProfitPct(new BigDecimal("1.0"));
        paperExit.setStopLossPct(new BigDecimal("0.6"));
        paperExit.setTimeStopMinutes(240);
        paperExit.setTimeStopCloseOnlyIfNonPositive(true);
        paperExit.setBarMinutes(60);
        properties.setPaperExit(paperExit);
        return properties;
    }


    private List<Kline> hourlyKlinesEndingAt(Instant lastOpenTime, Instant lastCloseTime) {
        Instant firstOpenTime = lastOpenTime.minusSeconds(219L * 60L * 60L);
        return java.util.stream.IntStream.range(0, 220)
                .mapToObj(index -> {
                    Instant openTime = firstOpenTime.plusSeconds(index * 60L * 60L);
                    return Kline.builder()
                            .symbol("BTCUSDT")
                            .interval("1h")
                            .openTime(openTime)
                            .closeTime(index == 219 ? lastCloseTime : openTime.plusSeconds(3599))
                            .open(new BigDecimal("100"))
                            .high(new BigDecimal("101"))
                            .low(new BigDecimal("97"))
                            .close(new BigDecimal("98"))
                            .closed(true)
                            .build();
                })
                .toList();
    }

    private Kline kline(String symbol) {
        Instant openTime = Instant.parse("2026-06-07T13:05:00Z");
        return Kline.builder()
                .symbol(symbol)
                .interval("5m")
                .openTime(openTime)
                .closeTime(openTime.plusSeconds(300))
                .open(new BigDecimal("100"))
                .high(new BigDecimal("100.2"))
                .low(new BigDecimal("99.8"))
                .close(new BigDecimal("100.1"))
                .closed(true)
                .build();
    }

    private PaperPositionEntity position(PositionSide side) {
        return PaperPositionEntity.builder()
                .id(1L)
                .symbol("BTCUSDT")
                .side(side)
                .status(PaperPositionStatus.OPEN)
                .entryAction(side == PositionSide.LONG ? EntryAction.ENTER_LONG : EntryAction.ENTER_SHORT)
                .entryPrice(new BigDecimal("100"))
                .quantity(BigDecimal.ONE)
                .notionalUsdt(new BigDecimal("100"))
                .leverage(3)
                .openedAt(Instant.parse("2026-06-07T13:03:59Z"))
                .takeProfitPct(new BigDecimal("1.0"))
                .stopLossPct(new BigDecimal("0.6"))
                .timeStopMinutes(240)
                .build();
    }
}
