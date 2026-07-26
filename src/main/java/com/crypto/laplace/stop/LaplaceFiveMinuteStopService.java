package com.crypto.laplace.stop;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceFiveMinuteStopService {
    private static final int MAX_CANDLES = 40;
    private static final long FIVE_MINUTE_SECONDS = 300;
    private final BinanceFuturesClient client;
    private final LaplacePaperPositionRepository positions;
    private final LaplacePaperExecutionService execution;
    private final LaplacePaperTradeCoordinator coordinator;

    public void checkOpenPositions() {
        Instant started = Instant.now();
        List<LaplacePaperPositionEntity> open = openPositions();
        Set<String> errors = new HashSet<>();
        for (LaplacePaperPositionEntity position : open) {
            try {
                inspect(position, client.getKlines(position.getSymbol(), "5m", MAX_CANDLES), Instant.now());
            } catch (RuntimeException exception) {
                errors.add(position.getSymbol());
                log.error("LAPLACE_STOP_CANDLE_LOAD_FAILED symbol={} positionId={}",
                        position.getSymbol(), position.getId(), exception);
            }
        }
        log.info("LAPLACE_5M_STOP_CHECK openPositions={} symbols={} apiErrors={} startedAt={} finishedAt={}",
                open.size(), open.stream().map(LaplacePaperPositionEntity::getSymbol).distinct().count(),
                errors, started, Instant.now());
    }

    public void catchUpOpenPositions() {
        Instant now = Instant.now();
        for (LaplacePaperPositionEntity position : openPositions()) {
            ensureStopPrice(position);
            Instant cursor = position.getLastStopCheckedCandleOpenTime() == null
                    ? entryCandleOpen(position).plusSeconds(FIVE_MINUTE_SECONDS)
                    : position.getLastStopCheckedCandleOpenTime().plusSeconds(FIVE_MINUTE_SECONDS);
            while (cursor.isBefore(now) && position.getStatus() == LaplacePositionStatus.OPEN) {
                List<Kline> batch = client.getKlines(position.getSymbol(), "5m", MAX_CANDLES, cursor);
                boolean stopped = inspect(position, batch, now);
                if (stopped || batch.size() < MAX_CANDLES) break;
                cursor = batch.stream().map(Kline::getOpenTime).filter(java.util.Objects::nonNull)
                        .max(Instant::compareTo).orElse(cursor).plusSeconds(FIVE_MINUTE_SECONDS);
            }
        }
    }

    boolean inspect(LaplacePaperPositionEntity position, List<Kline> candles, Instant now) {
        ensureStopPrice(position);
        Instant entryOpen = entryCandleOpen(position);
        List<Kline> closed = candles.stream()
                .filter(c -> c != null && c.getOpenTime() != null && c.getCloseTime() != null)
                .filter(c -> !c.getCloseTime().isAfter(now) && !Boolean.FALSE.equals(c.getClosed()))
                .filter(c -> c.getOpenTime().isAfter(entryOpen))
                .filter(c -> position.getLastStopCheckedCandleOpenTime() == null
                        || c.getOpenTime().isAfter(position.getLastStopCheckedCandleOpenTime()))
                .sorted(Comparator.comparing(Kline::getOpenTime))
                .toList();
        for (Kline candle : closed) {
            if (touchesStop(position, candle)) {
                closeStop(position, candle);
                return true;
            }
            position.setLastStopCheckedCandleOpenTime(candle.getOpenTime());
            position.setLastChecked5mCandleCloseTime(candle.getCloseTime());
            positions.save(position);
        }
        return false;
    }

    private boolean touchesStop(LaplacePaperPositionEntity position, Kline candle) {
        return position.getSide() == PositionSide.LONG
                ? candle.getLow().compareTo(position.getStopPrice()) <= 0
                : candle.getHigh().compareTo(position.getStopPrice()) >= 0;
    }

    private void closeStop(LaplacePaperPositionEntity position, Kline candle) {
        BigDecimal simulatedExecution = historicalExecutionPrice(position, candle);
        execution.closeByStop(position.getId(), candle, simulatedExecution, "FIVE_MINUTE_STOP_SIMULATION")
                .ifPresent(closed -> {
                    coordinator.onStopLossClosed(closed.getSymbol(), closed.getEntryRawSignal(),
                            closed.getSide().name(), closed.getId(), closed.getExitTime());
                    execution.drainOutbox();
                });
    }

    static BigDecimal historicalExecutionPrice(LaplacePaperPositionEntity position, Kline candle) {
        if (position.getSide() == PositionSide.LONG) {
            return candle.getOpen().compareTo(position.getStopPrice()) < 0
                    ? candle.getOpen() : position.getStopPrice();
        }
        return candle.getOpen().compareTo(position.getStopPrice()) > 0
                ? candle.getOpen() : position.getStopPrice();
    }

    private void ensureStopPrice(LaplacePaperPositionEntity position) {
        if (position.getStopPrice() != null || position.getEntryExecutionPrice() == null) return;
        position.setStopLossPct(new BigDecimal("0.055"));
        position.setStopPrice(position.getSide() == PositionSide.LONG
                ? position.getEntryExecutionPrice().multiply(new BigDecimal("0.945"))
                : position.getEntryExecutionPrice().multiply(new BigDecimal("1.055")));
        positions.save(position);
    }

    static Instant entryCandleOpen(LaplacePaperPositionEntity position) {
        long epoch = position.getEntryTime().getEpochSecond();
        return Instant.ofEpochSecond((epoch / FIVE_MINUTE_SECONDS) * FIVE_MINUTE_SECONDS);
    }

    private List<LaplacePaperPositionEntity> openPositions() {
        return positions.findByStrategyAndStatus(
                LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN);
    }
}
