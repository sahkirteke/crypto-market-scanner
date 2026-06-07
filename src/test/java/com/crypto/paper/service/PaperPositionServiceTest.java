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
import com.crypto.domain.model.EntrySignal;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.EntrySignalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
        when(repository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of());
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
        when(repository.existsBySymbolAndStatus("BTCUSDT", PaperPositionStatus.OPEN)).thenReturn(true);

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenPositionsReachedDoesNotOpenPosition() {
        when(repository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of(
                position(PositionSide.LONG), position(PositionSide.LONG), position(PositionSide.SHORT),
                position(PositionSide.SHORT), position(PositionSide.LONG)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenShortPositionsReachedDoesNotOpenShortPosition() {
        when(repository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of(
                position(PositionSide.SHORT), position(PositionSide.SHORT), position(PositionSide.SHORT)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_SHORT, PositionSide.SHORT, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
    }

    @Test
    void maxOpenLongPositionsReachedDoesNotOpenLongPosition() {
        when(repository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of(
                position(PositionSide.LONG), position(PositionSide.LONG), position(PositionSide.LONG)
        ));

        PaperPositionEntity opened = service.openPosition(signal("BTCUSDT", EntryAction.ENTER_LONG, PositionSide.LONG, "10", RiskLevel.LOW));

        assertThat(opened).isNull();
        verify(repository, never()).save(any(PaperPositionEntity.class));
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
        verify(repository, never()).existsBySymbolAndStatus("SOLUSDT", PaperPositionStatus.OPEN);
        verify(repository, Mockito.times(2)).save(any(PaperPositionEntity.class));
    }

    private EntrySignal signal(String symbol, EntryAction action, PositionSide side, String entryPrice, RiskLevel riskLevel) {
        return EntrySignal.builder()
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
