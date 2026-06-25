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
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.EntrySignal;
import com.crypto.domain.model.Kline;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.EntrySignalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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
    void validEnterShortSignalOpensInvertedLongPaperPosition() {
        EntrySignal signal = signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "5.115", RiskLevel.LOW);

        PaperPositionEntity opened = service.openPosition(signal);

        ArgumentCaptor<PaperPositionEntity> captor = ArgumentCaptor.forClass(PaperPositionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(opened).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(captor.getValue().getSide()).isEqualTo(PositionSide.LONG);
        assertThat(captor.getValue().getSourceSignalSide()).isEqualTo(PositionSide.SHORT);
        assertThat(captor.getValue().getExecutionSide()).isEqualTo(PositionSide.LONG);
        assertThat(captor.getValue().getSignalInverted()).isTrue();
        assertThat(captor.getValue().getInversionReason()).isEqualTo("SHORT_SIGNAL_INVERTED_TO_LONG");
        assertThat(captor.getValue().getInitialStop()).isLessThan(captor.getValue().getEntryPrice());
        assertThat(captor.getValue().getTp1()).isGreaterThan(captor.getValue().getEntryPrice());
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
        assertThat(opened.getSide()).isEqualTo(PositionSide.LONG);
        assertThat(opened.getSourceSignalSide()).isEqualTo(PositionSide.LONG);
        assertThat(opened.getExecutionSide()).isEqualTo(PositionSide.LONG);
        assertThat(opened.getSignalInverted()).isFalse();
        assertThat(opened.getInversionReason()).isNull();
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
        assertThat(captor.getValue()).containsEntry("side", "LONG");
        assertThat(captor.getValue()).containsEntry("signalSide", "SHORT");
        assertThat(captor.getValue()).containsEntry("executionSide", "LONG");
        assertThat(captor.getValue()).containsEntry("signalInverted", true);
        assertThat(captor.getValue()).containsEntry("inversionReason", "SHORT_SIGNAL_INVERTED_TO_LONG");
        assertThat(captor.getValue()).containsKeys("positionId", "entryPrice", "tp1", "tp2", "slPrice");
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
    void maxOpenShortPositionsDoesNotBlockInvertedShortSignalBecauseExecutionIsLong() {
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(
                position(PositionSide.SHORT), position(PositionSide.SHORT), position(PositionSide.SHORT)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

        assertThat(opened).isNotNull();
        assertThat(opened.getSide()).isEqualTo(PositionSide.LONG);
        verify(repository).save(any(PaperPositionEntity.class));
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
    void maxOpenLongPositionsReachedBlocksInvertedEnterShortSignal() {
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(
                position(PositionSide.LONG), position(PositionSide.LONG), position(PositionSide.LONG)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

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
    void capsTpSlAndKeepsInvertedSignalsLong() {
        EntrySignal signal = signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "100", RiskLevel.LOW);
        signal.setAtr14_1h(new BigDecimal("2.0"));

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNotNull();
        assertThat(opened.getSide()).isEqualTo(PositionSide.LONG);
        assertThat(opened.getSignalInverted()).isTrue();
        assertThat(opened.getTp1()).isEqualByComparingTo("101.300000000000");
        assertThat(opened.getTp2()).isEqualByComparingTo("101.800000000000");
        assertThat(opened.getInitialStop()).isEqualByComparingTo("99.000000000000");
    }

    @Test
    void keepsLowerExistingTpSlPercentages() {
        EntrySignal signal = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "100", RiskLevel.LOW);
        signal.setAtr14_1h(new BigDecimal("0.7"));

        PaperPositionEntity opened = service.openPosition(signal);

        assertThat(opened).isNotNull();
        assertThat(opened.getTp1()).isEqualByComparingTo("100.840000000000");
        assertThat(opened.getTp2()).isEqualByComparingTo("101.680000000000");
        assertThat(opened.getInitialStop()).isEqualByComparingTo("99.160000000000");
    }

    @Test
    void rangePosBoundariesUsePreviousClosedOneHourCandle() {
        assertThat(service.openPosition(signalWithRangePosition("AUSDT", "0.400"))).isNull();
        assertThat(service.openPosition(signalWithRangePosition("BUSDT", "0.401"))).isNotNull();
        assertThat(service.openPosition(signalWithRangePosition("CUSDT", "0.800"))).isNotNull();
        assertThat(service.openPosition(signalWithRangePosition("DUSDT", "0.801"))).isNull();
    }

    @Test
    void bbWidthFilterAppliesToEveryEntry() {
        EntrySignal passing = signal("AUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW);
        passing.setBbWidth(new BigDecimal("0.149"));
        EntrySignal equal = signal("BUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW);
        equal.setBbWidth(new BigDecimal("0.15"));
        EntrySignal missing = signal("CUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW);
        missing.setBbWidth(null);

        assertThat(service.openPosition(passing)).isNotNull();
        assertThat(service.openPosition(equal)).isNull();
        assertThat(service.openPosition(missing)).isNull();
    }

    @Test
    void rangePosUsesLastClosedOneHourCandleBeforeEntryTime() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        ReflectionTestUtils.setField(service, "binanceFuturesClient", client);
        EntrySignal signal = signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "105", RiskLevel.LOW);
        signal.setPrevious1hLow(null);
        signal.setPrevious1hHigh(null);
        Instant entryTime = Instant.parse("2026-06-23T15:00:30Z");
        when(client.getKlines("BTCUSDT", "1h", 4)).thenReturn(List.of(
                kline("2026-06-23T14:00:00Z", "2026-06-23T15:00:00Z", "110", "100", true),
                kline("2026-06-23T15:00:00Z", "2026-06-23T16:00:00Z", "1000", "900", false)
        ));

        PaperPositionService.RangePos1hResult result = service.resolveRangePos1h(signal, new BigDecimal("105"), entryTime);

        assertThat(result.lastClosed1hLow()).isEqualByComparingTo("100");
        assertThat(result.lastClosed1hHigh()).isEqualByComparingTo("110");
        assertThat(result.rangePos1h()).isEqualByComparingTo("0.500000000000");
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo("OK");
    }

    @Test
    void rangePosBoundaryStatusIsCalculated() {
        assertThat(service.resolveRangePos1h(signalWithRangePosition("AUSDT", "0.400"), new BigDecimal("10"), Instant.now()).passed()).isFalse();
        assertThat(service.resolveRangePos1h(signalWithRangePosition("BUSDT", "0.800"), new BigDecimal("10"), Instant.now()).passed()).isTrue();
        assertThat(service.resolveRangePos1h(signalWithRangePosition("CUSDT", "0.810"), new BigDecimal("10"), Instant.now()).passed()).isFalse();
    }

    @Test
    void rangePosIsPersistedForInvertedAndFourHourEntries() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        ReflectionTestUtils.setField(service, "binanceFuturesClient", client);
        when(client.getAllBookTickers()).thenReturn(List.of(bookTicker("ETHUSDT", "105"), bookTicker("SOLUSDT", "105")));
        when(client.getKlines("ETHUSDT", "1h", 4)).thenReturn(List.of(kline("2026-06-23T14:00:00Z", "2026-06-23T15:00:00Z", "110", "100", true)));
        when(client.getKlines("SOLUSDT", "1h", 4)).thenReturn(List.of(kline("2026-06-23T14:00:00Z", "2026-06-23T15:00:00Z", "110", "100", true)));
        EntrySignal inverted = signal("ETHUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "105", RiskLevel.LOW);
        inverted.setPrevious1hLow(null);
        inverted.setPrevious1hHigh(null);
        EntrySignal fourHour = signal("SOLUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "105", RiskLevel.LOW);
        fourHour.setSourceScanType(ScanType.FOUR_HOUR);
        fourHour.setPrevious1hLow(null);
        fourHour.setPrevious1hHigh(null);

        PaperPositionEntity invertedOpened = service.openPosition(inverted);
        PaperPositionEntity fourHourOpened = service.openPosition(fourHour);

        assertThat(invertedOpened.getSignalInverted()).isTrue();
        assertThat(invertedOpened.getRangePos1h()).isEqualByComparingTo("0.500000000000");
        assertThat(invertedOpened.getRangePos1hPassed()).isTrue();
        assertThat(invertedOpened.getRangePos1hStatus()).isEqualTo("OK");
        assertThat(fourHourOpened.getSourceScanType()).isEqualTo(ScanType.FOUR_HOUR);
        assertThat(fourHourOpened.getRangePos1h()).isEqualByComparingTo("0.500000000000");
        assertThat(fourHourOpened.getLastClosed1hLow()).isEqualByComparingTo("100");
        assertThat(fourHourOpened.getLastClosed1hHigh()).isEqualByComparingTo("110");
    }

    private BookTicker bookTicker(String symbol, String mid) {
        return BookTicker.builder()
                .symbol(symbol)
                .bidPrice(new BigDecimal(mid))
                .askPrice(new BigDecimal(mid))
                .midPrice(new BigDecimal(mid))
                .build();
    }

    private Kline kline(String openTime, String closeTime, String high, String low, boolean closed) {
        return Kline.builder()
                .symbol("BTCUSDT")
                .interval("1h")
                .openTime(Instant.parse(openTime))
                .closeTime(Instant.parse(closeTime))
                .open(new BigDecimal(low))
                .high(new BigDecimal(high))
                .low(new BigDecimal(low))
                .close(new BigDecimal(high).add(new BigDecimal(low)).divide(new BigDecimal("2")))
                .closed(closed)
                .build();
    }

    private EntrySignal signalWithRangePosition(String symbol, String rangePos) {
        EntrySignal signal = signal(symbol, EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW);
        signal.setPrevious1hLow(BigDecimal.ZERO);
        signal.setPrevious1hHigh(new BigDecimal("10").divide(new BigDecimal(rangePos), 12, java.math.RoundingMode.HALF_UP));
        return signal;
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
                .previous1hLow(new BigDecimal("8"))
                .previous1hHigh(new BigDecimal("12"))
                .ema20_1h(new BigDecimal("10.00"))
                .rsi14_1h(new BigDecimal("55"))
                .macdHist_1h(new BigDecimal("0.10"))
                .atr14_1h(new BigDecimal("0.20"))
                .volumeRatio_1h(new BigDecimal("1.20"))
                .bbWidth(new BigDecimal("0.14"))
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

    private PaperPositionEntity position(PositionSide side) {
        return PaperPositionEntity.builder()
                .symbol(side + "USDT")
                .side(side)
                .status(PaperPositionStatus.OPEN)
                .build();
    }
}
