package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.V20PaperSummaryReport;
import com.crypto.persistence.entity.PaperPositionEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class V20PaperSummaryServiceTest {
    @Test
    void summarySeparatesLongShortAndTotal() {
        V20PaperSummaryReport report = new V20PaperSummaryService(new V20PnlCalculator(new com.crypto.scanner.config.ScannerProperties())).summarize(List.of(
                position(PositionSide.LONG, "TAKE_PROFIT", "10", "0.5"),
                position(PositionSide.LONG, "STOP_LOSS", "-4", "0.5"),
                position(PositionSide.SHORT, "TAKE_PROFIT", "6", "0.5")
        ));

        assertThat(report.longSummary().tradeCount()).isEqualTo(2);
        assertThat(report.shortSummary().tradeCount()).isEqualTo(1);
        assertThat(report.totalSummary().tradeCount()).isEqualTo(3);
        assertThat(report.totalSummary().takeProfitCount()).isEqualTo(2);
        assertThat(report.totalSummary().stopLossCount()).isEqualTo(1);
        assertThat(report.totalSummary().leveragedNetPnlUsdt()).isEqualByComparingTo("12.00000000");
    }

    private PaperPositionEntity position(PositionSide side, String reason, String pnl, String fee) {
        return PaperPositionEntity.builder().strategyVersion("V20").side(side).exitReason(reason)
                .leveragedNetPnlUsdt(new BigDecimal(pnl)).leveragedTotalFeeUsdt(new BigDecimal(fee)).unleveragedNetPnlUsdt(new BigDecimal(pnl)).unleveragedTotalFeeUsdt(new BigDecimal(fee)).build();
    }
}
