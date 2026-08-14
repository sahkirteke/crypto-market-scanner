package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplaceExecutionPriceProvider;
import com.crypto.laplace.execution.LaplaceInvertedFalseExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionEntity;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceInvertedFalseProfitLockService {
    private static final int SCALE = 12;
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final LaplaceInvertedFalsePositionRepository positions;
    private final BinanceFuturesClient client;
    private final LaplacePnlCalculator pnl;
    private final LaplaceInvertedFalseExecutionService execution;
    private final LaplaceTemporaryStateResetService reset;
    private final Clock clock;

    public void evaluate() {
        if (!runtime.isActive()) return;
        var session = runtime.current();
        List<LaplaceInvertedFalsePositionEntity> open = positions.findBySessionIdAndStatus(session.getSessionId(), LaplacePositionStatus.OPEN);
        BigDecimal realized = sessionNetPnl(session.getSessionId());
        Map<String, BookTicker> tickers = open.isEmpty() ? Map.of() : client.getAllBookTickers().stream().collect(Collectors.toMap(
                BookTicker::getSymbol, Function.identity(), (first, ignored) -> first));
        BigDecimal openNet = BigDecimal.ZERO;
        BigDecimal exitFees = BigDecimal.ZERO;
        for (var position : open) {
            BigDecimal price = closingPrice(position, tickers);
            openNet = openNet.add(pnl.gross(position.getSide(), position.getEntryExecutionPrice(), price, position.getQuantity()))
                    .subtract(position.getEntryFee());
            exitFees = exitFees.add(price.multiply(position.getQuantity()).multiply(position.getEntryFeeRate())
                    .setScale(SCALE, RoundingMode.HALF_UP));
        }
        BigDecimal feeAfterTotalPnl = realized.add(openNet).subtract(exitFees);
        if (feeAfterTotalPnl.compareTo(runtime.target(session)) < 0) return;
        if (!runtime.beginLiquidation(realized, openNet, exitFees, feeAfterTotalPnl)) return;
        liquidate(open, tickers, session.getSessionId());
    }

    public void retryLiquidation() {
        var session = runtime.current();
        if (session.getRuntimeState() != com.crypto.laplace.model.LaplaceRuntimeState.LIQUIDATING) return;
        List<LaplaceInvertedFalsePositionEntity> open = positions.findBySessionIdAndStatus(session.getSessionId(), LaplacePositionStatus.OPEN);
        Map<String, BookTicker> tickers = open.isEmpty() ? Map.of() : client.getAllBookTickers().stream().collect(Collectors.toMap(
                BookTicker::getSymbol, Function.identity(), (first, ignored) -> first));
        liquidate(open, tickers, session.getSessionId());
    }

    private void liquidate(List<LaplaceInvertedFalsePositionEntity> open, Map<String, BookTicker> tickers, String sessionId) {
        for (var position : open) {
            BigDecimal price = closingPrice(position, tickers);
            String type = position.getSide() == PositionSide.LONG ? "BID" : "ASK";
            BookTicker ticker = tickers.get(position.getSymbol());
            var quote = new LaplaceExecutionPriceProvider.Price(price, ticker.getBidPrice(), ticker.getAskPrice(), type, "BOOK_TICKER");
            try {
                execution.closeForProfitLock(position.getId(), quote, clock.instant());
            } catch (RuntimeException failure) {
                log.error("LAPLACE_PROFIT_LOCK_CLOSE_FAILED positionId={} symbol={}", position.getId(), position.getSymbol(), failure);
                return;
            }
        }
        List<LaplaceInvertedFalsePositionEntity> remaining = positions.findBySessionIdAndStatus(sessionId, LaplacePositionStatus.OPEN);
        if (!remaining.isEmpty()) return;
        List<LaplaceInvertedFalsePositionEntity> closed = positions.findBySessionIdAndStatus(sessionId, LaplacePositionStatus.CLOSED);
        BigDecimal actual = closed.stream().map(p -> p.getNetPnl() == null ? BigDecimal.ZERO : p.getNetPnl())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant lastExit = closed.stream().map(LaplaceInvertedFalsePositionEntity::getExitTime).max(Instant::compareTo).orElse(clock.instant());
        runtime.recordLiquidationResult(actual);
        reset.clearInvertedFalse();
        runtime.startCooldown(lastExit);
        reset.clearSharedIfUnused();
        execution.drainTradeEvents();
    }

    private BigDecimal sessionNetPnl(String sessionId) {
        return positions.findBySessionIdAndStatus(sessionId, LaplacePositionStatus.CLOSED).stream()
                .map(p -> p.getNetPnl() == null ? BigDecimal.ZERO : p.getNetPnl()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal closingPrice(LaplaceInvertedFalsePositionEntity position, Map<String, BookTicker> tickers) {
        BookTicker ticker = tickers.get(position.getSymbol());
        BigDecimal price = ticker == null ? null : position.getSide() == PositionSide.LONG ? ticker.getBidPrice() : ticker.getAskPrice();
        if (price == null || price.signum() <= 0) throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE");
        return price;
    }
}
