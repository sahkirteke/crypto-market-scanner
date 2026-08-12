package com.crypto.laplace.service;

import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaplacePositionManagementService {
    private final LaplacePaperPositionRepository positions;
    private final FiveMinuteKlineService klines;
    private final LaplacePaperExecutionService execution;

    public PositionCatchUpResult catchUp(String positionId, Instant cutoffInclusive) {
        var position = positions.findById(positionId).orElse(null);
        if (position == null || position.getStatus() != LaplacePositionStatus.OPEN) return new PositionCatchUpResult(0, true, null);
        Instant after = position.getLastManagedFiveMinuteCandleCloseTime() != null
                ? position.getLastManagedFiveMinuteCandleCloseTime() : position.getEntryCandleCloseTime();
        int count = 0; Instant last = after;
        try {
            for (var candle : klines.loadClosedRange(position.getSymbol(), after, cutoffInclusive)) {
                PositionManagementOutcome outcome = execution.evaluateClosedFiveMinuteCandle(positionId, candle);
                if (outcome != PositionManagementOutcome.ALREADY_PROCESSED) { count++; last = candle.getCloseTime(); }
                if (outcome == PositionManagementOutcome.CLOSED_STOP_LOSS
                        || outcome == PositionManagementOutcome.CLOSED_BREAK_EVEN
                        || outcome == PositionManagementOutcome.CLOSED_SHORT_EMA20_ACCEPTANCE)
                    return new PositionCatchUpResult(count, true, last);
            }
            return new PositionCatchUpResult(count, false, last);
        } catch (RuntimeException e) {
            log.error("LAPLACE_POSITION_CATCH_UP_FAILED positionId={} cutoff={} error={}", positionId, cutoffInclusive, e.getMessage(), e);
            throw e;
        }
    }
}
