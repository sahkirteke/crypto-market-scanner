package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.EntrySignal;
import com.crypto.paper.log.V20PaperJsonlLogService;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.EntrySignalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class PaperPositionServiceTest {
    private PaperPositionRepository repository;
    private EntrySignalService entrySignalService;
    private ScannerProperties scannerProperties;
    private PaperPositionService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaperPositionRepository.class);
        entrySignalService = mock(EntrySignalService.class);
        scannerProperties = new ScannerProperties();
        scannerProperties.getV20().setEnabled(false);
        service = new PaperPositionService(
                repository,
                entrySignalService,
                scannerProperties,
                new JsonTextMapper(new ObjectMapper())
        );
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of());
        when(repository.save(any(PaperPositionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validEnterShortSignalOpensPaperPosition() {
        EntrySignal signal = signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "5.115", RiskLevel.LOW);

        PaperPositionEntity opened = service.openPosition(signal);

        ArgumentCaptor<PaperPositionEntity> captor = ArgumentCaptor.forClass(PaperPositionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(opened).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(captor.getValue().getSide()).isEqualTo(PositionSide.SHORT);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("19.550342130987"));
    }


    @Test
    void openPositionCopiesEntryBollingerSnapshot() {
        EntrySignal signal = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW);
        signal.setBbScore(new BigDecimal("-4"));
        signal.setBbPercentB(new BigDecimal("0.92"));
        signal.setBbWidth(new BigDecimal("0.20"));
        signal.setBbUpper(new BigDecimal("11"));
        signal.setBbMiddle(new BigDecimal("10"));
        signal.setBbLower(new BigDecimal("9"));
        signal.setBbUpperTouched(true);
        signal.setBbLowerTouched(false);
        signal.setBbUpperClosedOutside(false);
        signal.setBbLowerClosedOutside(false);
        signal.setBbReasons(List.of("LONG_BB_CHASE_RISK"));

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNotNull();
        assertThat(opened.getEntryBbScore()).isEqualByComparingTo("-4");
        assertThat(opened.getEntryBbPercentB()).isEqualByComparingTo("0.92");
        assertThat(opened.getEntryBbReasonsJson()).contains("LONG_BB_CHASE_RISK");
    }

    @Test
    void openPositionWritesEntryTradeLog() {
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ReflectionTestUtils.setField(service, "jsonlDecisionLogService", jsonl);
        EntrySignal signal = signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "5.115", RiskLevel.LOW);

        PaperPositionEntity opened = service.openPosition(signal);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(jsonl).logPaperTrade(captor.capture());
        assertThat(opened).isNotNull();
        assertThat(captor.getValue()).containsEntry("type", "ENTRY");
        assertThat(captor.getValue()).containsEntry("symbol", "ETHUSDT");
        assertThat(captor.getValue()).containsEntry("side", "SHORT");
        assertThat(captor.getValue()).containsKeys("positionId", "entryPrice", "tp1", "tp2", "slPrice");
    }

    @Test
    void v20PositionOpenedLogIsWrittenOnceAfterSaveWithPositionIdAndSnapshot() {
        scannerProperties.getV20().setEnabled(true);
        mockBookTicker("BTCUSDT", "99", "101", "100");
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        when(v20Log.formatTr(any())).thenReturn("2026-06-13T18:42:10.125+03:00");
        when(repository.save(any(PaperPositionEntity.class))).thenAnswer(invocation -> {
            PaperPositionEntity p = invocation.getArgument(0);
            p.setId(42L);
            return p;
        });

        PaperPositionEntity opened = service.openPosition(v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100"));

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(v20Log, Mockito.times(1)).log(captor.capture());
        assertThat(opened.getId()).isEqualTo(42L);
        assertThat(opened.getV20SignalSnapshotJson()).contains("signalScore", "qualityStatus", "INSUFFICIENT_HISTORY");
        assertThat(captor.getValue()).containsEntry("eventType", "POSITION_OPENED");
        assertThat(captor.getValue()).containsEntry("positionId", 42L);
        assertThat(captor.getValue()).containsEntry("qualityStatus", "INSUFFICIENT_HISTORY");
        assertThat(captor.getValue()).containsEntry("feeMode", "MAKER");
        assertThat(captor.getValue()).containsEntry("feeRate", new BigDecimal("0.0002"));
        assertThat(captor.getValue()).containsEntry("slippagePct", BigDecimal.ZERO);
        assertThat(captor.getValue()).containsEntry("marginUsdt", new BigDecimal("100"));
        assertThat(captor.getValue()).containsEntry("leverage", 5);
        assertThat(captor.getValue()).containsEntry("leveragedNotionalUsdt", new BigDecimal("500"));
        assertThat(captor.getValue()).containsEntry("unleveragedNotionalUsdt", new BigDecimal("100"));
    }

    @Test
    void noEntryDoesNotWritePositionOpened() {
        scannerProperties.getV20().setEnabled(true);
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.NO_ENTRY, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(v20Log, never()).log(any());
    }

    @Test
    void v20ImmediateEntryDoesNotRequireLegacyIndicators() {
        scannerProperties.getV20().setEnabled(true);
        mockBookTicker("BTCUSDT", "99", "101", "100");
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        when(v20Log.formatTr(any())).thenReturn("2026-06-13T18:42:10.125+03:00");
        EntrySignal signal = v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100");
        signal.setEntryPrice(null);

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNotNull();
        assertThat(opened.getEntryPrice()).isEqualByComparingTo("100");
        verify(repository).save(any(PaperPositionEntity.class));
        verify(v20Log).log(any());
    }

    @Test
    void legacyEntryStillRequiresLegacyIndicators() {
        scannerProperties.getV20().setEnabled(false);
        EntrySignal signal = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100", RiskLevel.LOW);
        signal.setClose1h(null);
        signal.setEma20_1h(null);
        signal.setRsi14_1h(null);
        signal.setMacdHist_1h(null);
        signal.setAtr14_1h(null);
        signal.setVolumeRatio_1h(null);

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void v20EntryRejectsMissingBookTicker() {
        scannerProperties.getV20().setEnabled(true);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        ReflectionTestUtils.setField(service, "binanceFuturesClient", client);
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        when(client.getAllBookTickers()).thenReturn(List.of());

        PaperPositionEntity opened = service.openPosition(v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100"));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
        verify(v20Log, never()).log(any());
    }

    @Test
    void v20EntryRejectsInvalidQuantity() {
        scannerProperties.getV20().setEnabled(true);
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        mockBookTicker("BTCUSDT", "999999999999999999999", "1000000000000000000001", "1000000000000000000000");

        PaperPositionEntity opened = service.openPosition(v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "1000000000000000000000"));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
        verify(v20Log, never()).log(any());
    }

    @Test
    void noEntrySignalDoesNotOpenPosition() {
        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.NO_ENTRY, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void paperDisabledDoesNotOpenPosition() {
        scannerProperties.getPaper().setEnabled(false);

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void invalidEntryPriceDoesNotOpenPosition() {
        assertThat(service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, null, RiskLevel.LOW))).isNull();
        assertThat(service.openPosition(signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "0", RiskLevel.LOW))).isNull();

        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void highRiskDoesNotOpenWhenHighRiskDisabled() {
        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.HIGH));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void mediumRiskDoesNotOpenWhenMediumRiskDisabled() {
        scannerProperties.getPaper().setAllowMediumRisk(false);

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.MEDIUM));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void duplicateSymbolDoesNotOpenWhenMultipleOpenSameSymbolDisabled() {
        when(repository.existsBySymbolAndStatusIn("BTCUSDT", List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(true);

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenPositionsReachedDoesNotOpenPosition() {
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(
                position(PositionSide.LONG), position(PositionSide.LONG), position(PositionSide.SHORT),
                position(PositionSide.SHORT), position(PositionSide.LONG)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenShortPositionsReachedDoesNotOpenShortPosition() {
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(
                position(PositionSide.SHORT), position(PositionSide.SHORT), position(PositionSide.SHORT)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenLongPositionsReachedDoesNotOpenLongPosition() {
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(
                position(PositionSide.LONG), position(PositionSide.LONG), position(PositionSide.LONG)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }


    @Test
    void watchlistSignalDoesNotOpenPaperPosition() {
        EntrySignal signal = signal("SOLUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW);
        signal.setSourceClassification(CoinClassification.WATCHLIST);

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void openPositionsFromLatestSignalsFiltersWatchlistSignals() {
        EntrySignal strongSignal = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW);
        EntrySignal watchlistSignal = signal("SOLUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "30", RiskLevel.LOW);
        watchlistSignal.setSourceClassification(CoinClassification.WATCHLIST);
        when(entrySignalService.generateSignalsFromLatestScan()).thenReturn(List.of(strongSignal, watchlistSignal));

        List<PaperPositionEntity> opened = service.openPositionsFromLatestSignals();

        assertThat(opened).hasSize(1);
        verify(repository).save(any(PaperPositionEntity.class));
    }


    @Test
    void openPositionsFromScanRunReturnsPaperOpenSummary() {
        EntrySignal enterLong = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW);
        EntrySignal noEntry = signal("ETHUSDT", EntryAction.NO_ENTRY, PositionSide.SHORT, "20", RiskLevel.LOW);
        when(entrySignalService.generateSignalsFromScanRun(77L)).thenReturn(List.of(enterLong, noEntry));

        PaperPositionService.PaperOpenSummary summary = service.openPositionsFromScanRun(77L);

        assertThat(summary.candidateCount()).isEqualTo(2);
        assertThat(summary.signalCount()).isEqualTo(2);
        assertThat(summary.enterLongCount()).isEqualTo(1);
        assertThat(summary.enterShortCount()).isZero();
        assertThat(summary.noEntryCount()).isEqualTo(1);
        assertThat(summary.openedCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isEqualTo(1);
        assertThat(summary.openPositionsAfter()).isZero();
        assertThat(summary.openedPositions()).hasSize(1);
        verify(entrySignalService).generateSignalsFromScanRun(77L);
    }

    @Test
    void openPositionsOnlyOpensEnterLongAndEnterShortSignals() {
        List<EntrySignal> signals = List.of(
                signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW),
                signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "20", RiskLevel.LOW),
                signal("SOLUSDT", EntryAction.NO_ENTRY, PositionSide.LONG, "30", RiskLevel.LOW)
        );

        List<PaperPositionEntity> opened = service.openPositions(signals);

        assertThat(opened).hasSize(2);
        verify(repository, never()).existsBySymbolAndStatusIn("SOLUSDT", List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ));
        verify(repository, Mockito.times(2)).save(any(PaperPositionEntity.class));
    }

    @Test
    void v20StrongCandidatesOpenMultiplePositionsInSameScan() {
        scannerProperties.getV20().setEnabled(true);
        mockBookTicker("BTCUSDT", "99", "101", "100");
        mockBookTicker("ETHUSDT", "99", "101", "100");
        mockBookTicker("SOLUSDT", "99", "101", "100");
        V20PaperJsonlLogService v20Log = mock(V20PaperJsonlLogService.class);
        ReflectionTestUtils.setField(service, "v20PaperJsonlLogService", v20Log);
        when(v20Log.formatTr(any())).thenReturn("2026-06-13T18:42:10.125+03:00");
        List<EntrySignal> signals = List.of(
                v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100"),
                v20SignalWithoutLegacyIndicators("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "100"),
                v20SignalWithoutLegacyIndicators("SOLUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100")
        );

        List<PaperPositionEntity> opened = service.openPositions(signals);

        assertThat(opened).hasSize(3);
        verify(repository, Mockito.times(3)).save(any(PaperPositionEntity.class));
        verify(v20Log, Mockito.times(3)).log(any());
    }

    @Test
    void oneHourScanDoesNotOpenV20Position() {
        scannerProperties.getV20().setEnabled(true);
        mockBookTicker("BTCUSDT", "99", "101", "100");
        EntrySignal signal = v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100");
        signal.setSourceScanType(ScanType.ONE_HOUR);

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void fourHourScanCanOpenV20Position() {
        scannerProperties.getV20().setEnabled(true);
        mockBookTicker("BTCUSDT", "99", "101", "100");
        EntrySignal signal = v20SignalWithoutLegacyIndicators("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100");

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNotNull();
        assertThat(opened.getSourceScanType()).isEqualTo(ScanType.FOUR_HOUR);
        verify(repository).save(any(PaperPositionEntity.class));
    }

    private EntrySignal signal(String symbol, EntryAction action, PositionSide side, String entryPrice, RiskLevel riskLevel) {
        return EntrySignal.builder()
                .scanRunId(77L)
                .sourceScanType(ScanType.ONE_HOUR)
                .candidateId(123L)
                .symbol(symbol)
                .action(action)
                .side(side)
                .score(85)
                .longScore(85)
                .shortScore(80)
                .sourceClassification(side == PositionSide.SHORT ? CoinClassification.STRONG_SHORT : CoinClassification.STRONG_LONG)
                .directionBias(side == PositionSide.SHORT ? DirectionBias.SHORT : DirectionBias.LONG)
                .riskLevel(riskLevel)
                .entryPrice(entryPrice == null ? null : new BigDecimal(entryPrice))
                .close1h(new BigDecimal("10.50"))
                .ema20_1h(new BigDecimal("10.00"))
                .rsi14_1h(new BigDecimal("55"))
                .macdHist_1h(new BigDecimal("0.10"))
                .atr14_1h(new BigDecimal("0.20"))
                .volumeRatio_1h(new BigDecimal("1.20"))
                .fundingRate(new BigDecimal("0.0001"))
                .openInterest(new BigDecimal("1000000"))
                .marketBreadthPct(new BigDecimal("55"))
                .priceChange24hPct(new BigDecimal("1.5"))
                .spreadPct(new BigDecimal("0.02"))
                .signalReason("TEST_ENTRY")
                .reasons(List.of(ReasonTag.VOLUME_CONFIRMED))
                .warnings(List.of(ReasonTag.FUNDING_NORMAL))
                .build();
    }

    private EntrySignal v20SignalWithoutLegacyIndicators(String symbol, EntryAction action, PositionSide side, String entryPrice) {
        EntrySignal signal = signal(symbol, action, side, entryPrice, RiskLevel.LOW);
        signal.setEntryTrigger("V20_IMMEDIATE");
        signal.setSourceScanType(ScanType.FOUR_HOUR);
        signal.setClose1h(null);
        signal.setEma20_1h(null);
        signal.setRsi14_1h(null);
        signal.setMacdHist_1h(null);
        signal.setAtr14_1h(null);
        signal.setVolumeRatio_1h(null);
        signal.setScore(9);
        signal.setLongScore(side == PositionSide.LONG ? 9 : 0);
        signal.setShortScore(side == PositionSide.SHORT ? 9 : 0);
        signal.setSourceClassification(side == PositionSide.SHORT ? CoinClassification.STRONG_SHORT : CoinClassification.STRONG_LONG);
        return signal;
    }

    private void mockBookTicker(String symbol, String bid, String ask, String mid) {
        BinanceFuturesClient client;
        try {
            client = (BinanceFuturesClient) ReflectionTestUtils.getField(service, "binanceFuturesClient");
        } catch (IllegalArgumentException exception) {
            client = null;
        }
        if (client == null) {
            client = mock(BinanceFuturesClient.class);
            ReflectionTestUtils.setField(service, "binanceFuturesClient", client);
        }
        List<BookTicker> existing = client.getAllBookTickers();
        List<BookTicker> safeExisting = existing == null ? List.of() : existing;
        List<BookTicker> tickers = new java.util.ArrayList<>(safeExisting);
        tickers.add(BookTicker.builder()
                .symbol(symbol)
                .bidPrice(new BigDecimal(bid))
                .askPrice(new BigDecimal(ask))
                .midPrice(new BigDecimal(mid))
                .build());
        when(client.getAllBookTickers()).thenReturn(tickers);
    }

    private PaperPositionEntity position(PositionSide side) {
        return PaperPositionEntity.builder()
                .symbol(side + "USDT")
                .side(side)
                .status(PaperPositionStatus.OPEN)
                .build();
    }
}
