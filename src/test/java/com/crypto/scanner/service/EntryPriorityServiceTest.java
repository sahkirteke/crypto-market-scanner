package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.FourHourAlignment;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EntryPriorityServiceTest {
    private final EntryPriorityService service = new EntryPriorityService(new ScannerProperties());

    @Test
    void longAlignedFourHourReceivesBonuses() {
        EntryPriorityService.PriorityBreakdown breakdown = service.calculate(new EntryPriorityService.PriorityInput(
                PositionSide.LONG, 80, RiskLevel.LOW, MarketRegime.RISK_ON,
                new BigDecimal("0.02"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("55"), new BigDecimal("1.3"),
                new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("80"),
                new BigDecimal("60"), new BigDecimal("1"), new BigDecimal("1.1"),
                null, null, false));

        assertThat(breakdown.fourHourAlignment()).isEqualTo(FourHourAlignment.STRONG_ALIGNED);
        assertThat(breakdown.fourHourAlignmentBonus()).isEqualTo(25);
        assertThat(breakdown.finalEntryPriorityScore()).isEqualTo(118);
    }

    @Test
    void shortAgainstRiskOnAndFourHourUpReceivesHeavySoftPenalty() {
        EntryPriorityService.PriorityBreakdown breakdown = service.calculate(new EntryPriorityService.PriorityInput(
                PositionSide.SHORT, 90, RiskLevel.MEDIUM, MarketRegime.RISK_ON,
                new BigDecimal("0.04"), new BigDecimal("-0.0012"), new BigDecimal("-12"),
                new BigDecimal("32"), new BigDecimal("1.1"),
                new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("90"),
                new BigDecimal("28"), new BigDecimal("0.5"), new BigDecimal("0.9"),
                new BigDecimal("105"), new BigDecimal("100"), true));

        assertThat(breakdown.fourHourAlignment()).isEqualTo(FourHourAlignment.AGAINST_4H_TREND);
        assertThat(breakdown.sidePenalty()).isEqualTo(-95);
        assertThat(breakdown.symbolCooldownPenalty()).isEqualTo(-25);
        assertThat(breakdown.finalEntryPriorityScore()).isLessThan(0);
    }
}
