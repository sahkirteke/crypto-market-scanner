package com.crypto.scanner.service;

import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketBreadthService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 10;
    private static final int OUTPUT_SCALE = 2;

    public BigDecimal calculateMarketBreadthPct(List<TechnicalSnapshotPair> technicalPairs) {
        if (technicalPairs == null || technicalPairs.isEmpty()) {
            log.info("MARKET_BREADTH_READY evaluated={} aboveEma20={} marketBreadthPct={}",
                    0, 0, BigDecimal.ZERO);
            return BigDecimal.ZERO;
        }

        int evaluatedCount = 0;
        int aboveEma20Count = 0;

        for (TechnicalSnapshotPair pair : technicalPairs) {
            if (pair == null || !Boolean.TRUE.equals(pair.getReady())) {
                continue;
            }
            TechnicalSnapshot fourHour = pair.getFourHour();
            if (fourHour == null || fourHour.getClose() == null || fourHour.getEma20() == null) {
                continue;
            }

            evaluatedCount++;
            if (fourHour.getClose().compareTo(fourHour.getEma20()) > 0) {
                aboveEma20Count++;
            }
        }

        if (evaluatedCount == 0) {
            log.info("MARKET_BREADTH_READY evaluated={} aboveEma20={} marketBreadthPct={}",
                    evaluatedCount, aboveEma20Count, BigDecimal.ZERO);
            return BigDecimal.ZERO;
        }

        BigDecimal marketBreadthPct = BigDecimal.valueOf(aboveEma20Count)
                .divide(BigDecimal.valueOf(evaluatedCount), CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        log.info("MARKET_BREADTH_READY evaluated={} aboveEma20={} marketBreadthPct={}",
                evaluatedCount, aboveEma20Count, marketBreadthPct);
        return marketBreadthPct;
    }
}
