package com.crypto.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.service.V20PaperSummaryService;
import com.crypto.paper.service.V20PnlCalculator;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V20AnalysisSummaryServiceTest {
    @Test
    void analysisSummaryContainsMakerFeeAndBothPnlModels() {
        PaperPositionRepository repo = mock(PaperPositionRepository.class);
        ScannerProperties properties = new ScannerProperties();
        V20PnlCalculator calculator = new V20PnlCalculator(properties);
        V20AnalysisSummaryService service = new V20AnalysisSummaryService(repo, properties, calculator, new V20PaperSummaryService(calculator));
        when(repo.findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED)).thenReturn(List.of(
                position(PositionSide.LONG, "TAKE_PROFIT", "10"),
                position(PositionSide.SHORT, "STOP_LOSS", "-3")
        ));
        when(repo.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of());

        Map<String, Object> response = service.summary();
        Map<String, Object> summary = (Map<String, Object>) response.get("summary");
        Map<String, Object> total = (Map<String, Object>) summary.get("total");
        Map<String, Object> longSummary = (Map<String, Object>) summary.get("long");
        Map<String, Object> shortSummary = (Map<String, Object>) summary.get("short");

        assertThat(response.get("strategyVersion")).isEqualTo("V20");
        assertThat(total.get("tradeCount")).isEqualTo(2);
        assertThat(longSummary.get("tradeCount")).isEqualTo(1);
        assertThat(shortSummary.get("tradeCount")).isEqualTo(1);
        assertThat(total).containsKeys("unleveragedNetPnlUsdt", "leveragedNetPnlUsdt", "leveragedNetPnlPctOnMargin");
        Map<String, Object> paperConfig = (Map<String, Object>) response.get("paperConfig");
        assertThat(paperConfig.get("feeMode")).isEqualTo("MAKER");
        assertThat(paperConfig.get("feeRate")).isEqualTo(new BigDecimal("0.0002"));
        assertThat(paperConfig.get("slippagePct")).isEqualTo(BigDecimal.ZERO);
    }


    @Test
    void parameterizedSummaryKeepsV20Schema() {
        PaperPositionRepository repo = mock(PaperPositionRepository.class);
        ScannerProperties properties = new ScannerProperties();
        V20PnlCalculator calculator = new V20PnlCalculator(properties);
        V20AnalysisSummaryService service = new V20AnalysisSummaryService(repo, properties, calculator, new V20PaperSummaryService(calculator));
        when(repo.findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED)).thenReturn(List.of(position(PositionSide.LONG, "TAKE_PROFIT", "10")));
        when(repo.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(List.of());

        Map<String, Object> response = service.summary(10, Instant.parse("2026-06-13T00:00:00Z"), Instant.parse("2026-06-14T00:00:00Z"));

        assertThat(response).containsKeys("strategyVersion", "timezone", "generatedAtTr", "paperConfig", "summary", "openPositions", "recentClosedPositions");
        assertThat(response.get("strategyVersion")).isEqualTo("V20");
    }

    private PaperPositionEntity position(PositionSide side, String reason, String pnl) {
        return PaperPositionEntity.builder().strategyVersion("V20").side(side).exitReason(reason).closedAt(Instant.parse("2026-06-13T12:00:00Z"))
                .leveragedNetPnlUsdt(new BigDecimal(pnl)).leveragedTotalFeeUsdt(new BigDecimal("0.5"))
                .unleveragedNetPnlUsdt(new BigDecimal(pnl)).unleveragedTotalFeeUsdt(new BigDecimal("0.1")).build();
    }
}
