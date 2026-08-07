package com.crypto.laplace.api;

import com.crypto.api.dto.LaplaceAnalysisSummaryResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionResponse;
import com.crypto.api.dto.LaplaceOpenPositionCurrentStateResponse;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaplacePaperApiService implements ApplicationRunner {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 8;

    private final LaplacePaperPositionRepository repository;
    private final LaplaceStrategyProperties properties;
    private final BinanceFuturesClient binanceFuturesClient;

    @Value("${server.port:8080}")
    private String serverPort;

    @Override
    public void run(ApplicationArguments args) {
        log.info("PAPER_API_CONFIG_READY serverPort={} activeStrategy={} openPositionsEndpoint=/api/paper/positions/open analysisSummaryEndpoint=/api/analysis/summary positionSource=DATABASE",
                serverPort, properties.getActiveStrategy());
    }

    public boolean isLaplaceActive() {
        return LaplacePaperExecutionService.STRATEGY.equals(properties.getActiveStrategy())
                || "LAPLACE_KERNEL_30M".equals(properties.getActiveStrategy());
    }

    public List<LaplaceOpenPaperPositionResponse> findOpenPositions() {
        try {
            return repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)
                    .stream()
                    .sorted(Comparator.comparing(LaplacePaperPositionEntity::getEntryTime, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(LaplacePaperPositionEntity::getSymbol, Comparator.nullsLast(String::compareTo)))
                    .map(this::toOpenResponse)
                    .toList();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public List<LaplaceOpenPositionCurrentStateResponse> findCurrentOpenPositions() {
        final List<LaplacePaperPositionEntity> open;
        try {
            open = repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (open == null || open.isEmpty()) return List.of();
        try {
            List<BookTicker> all = binanceFuturesClient.getAllBookTickers();
            if (all == null || all.isEmpty()) throw new IllegalStateException("Binance book ticker data unavailable");
            Map<String, BookTicker> bySymbol = all.stream().filter(t -> t != null && t.getSymbol() != null)
                    .collect(Collectors.toMap(BookTicker::getSymbol, Function.identity(), (first, ignored) -> first));
            return open.stream().sorted(Comparator.comparing(LaplacePaperPositionEntity::getEntryTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(position -> currentState(position, requiredTicker(bySymbol, position.getSymbol())))
                    .toList();
        } catch (RuntimeException exception) {
            log.error("LAPLACE_CURRENT_POSITION_PRICE_UNAVAILABLE errorType={} errorMessage={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Current paper position prices are temporarily unavailable", exception);
        }
    }

    private LaplaceOpenPositionCurrentStateResponse currentState(LaplacePaperPositionEntity p, BookTicker ticker) {
        BigDecimal entry = requiredPositive(p.getEntryExecutionPrice(), "entry price");
        BigDecimal current = requiredPositive(p.getSide() == PositionSide.LONG ? ticker.getBidPrice() : ticker.getAskPrice(), "book price");
        BigDecimal remaining = p.getRemainingQuantity() == null ? p.getQuantity() : p.getRemainingQuantity();
        BigDecimal realizedGross = money(p.getRealizedGrossPnl());
        BigDecimal cumulativeExitFee = money(p.getCumulativeExitFee());
        BigDecimal signedMove = p.getSide() == PositionSide.LONG ? current.subtract(entry) : entry.subtract(current);
        BigDecimal priceMovePct = signedMove.multiply(ONE_HUNDRED).divide(entry, SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingGross = signedMove.multiply(remaining);
        BigDecimal estimatedExitFee = current.multiply(remaining).multiply(p.getEntryFeeRate());
        BigDecimal net = realizedGross.add(remainingGross).subtract(money(p.getEntryFee()))
                .subtract(cumulativeExitFee).subtract(estimatedExitFee);
        return new LaplaceOpenPositionCurrentStateResponse(p.getId(), p.getSymbol(), p.getSide(), p.getEntryTime(),
                entry, current, priceMovePct, net.setScale(SCALE, RoundingMode.HALF_UP));
    }

    private BookTicker requiredTicker(Map<String, BookTicker> tickers, String symbol) {
        BookTicker ticker = tickers.get(symbol);
        if (ticker == null) throw new IllegalStateException("Book ticker unavailable for " + symbol);
        return ticker;
    }

    private BigDecimal requiredPositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new IllegalStateException("Invalid " + field);
        return value;
    }

    public LaplaceAnalysisSummaryResponse summary() {
        try {
            List<LaplacePaperPositionEntity> open = repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN);
            List<LaplacePaperPositionEntity> closed = repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED);
            return buildSummary(open, closed);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private LaplaceOpenPaperPositionResponse toOpenResponse(LaplacePaperPositionEntity p) {
        return new LaplaceOpenPaperPositionResponse(
                p.getId(), p.getStrategy(), p.getStrategyVersion(), p.getSymbol(), p.getSide(), p.getStatus(),
                p.getEntryRawSignal(), p.getSignalInverted(), p.getSide(), null, p.getEntryCandleCloseTime(), p.getEntrySignalClosePrice(), p.getEntryTime(),
                p.getEntryExecutionPrice(), entryExecutionPriceType(p.getSide()), executionAction(p.getSide()),
                p.getQuantity(), p.getNotional(), p.getMargin(), p.getLeverage(), properties.getLaplace().getOrderType(),
                p.getEntryFeeRate(), p.getEntryFee(), null, p.getEntryTime(), p.getEntryTime());
    }

    private LaplaceAnalysisSummaryResponse buildSummary(List<LaplacePaperPositionEntity> open, List<LaplacePaperPositionEntity> closed) {
        List<LaplacePaperPositionEntity> safeOpen = open == null ? List.of() : open;
        List<LaplacePaperPositionEntity> safeClosed = closed == null ? List.of() : closed;
        long tradeCount = safeClosed.size();
        long longTradeCount = safeClosed.stream().filter(p -> p.getSide() == PositionSide.LONG).count();
        long shortTradeCount = safeClosed.stream().filter(p -> p.getSide() == PositionSide.SHORT).count();
        long winCount = safeClosed.stream().filter(p -> money(p.getNetPnl()).compareTo(BigDecimal.ZERO) > 0).count();
        long lossCount = safeClosed.stream().filter(p -> money(p.getNetPnl()).compareTo(BigDecimal.ZERO) < 0).count();
        long breakEvenCount = safeClosed.stream().filter(p -> money(p.getNetPnl()).compareTo(BigDecimal.ZERO) == 0).count();
        long longWinCount = safeClosed.stream().filter(p -> p.getSide() == PositionSide.LONG && money(p.getNetPnl()).compareTo(BigDecimal.ZERO) > 0).count();
        long shortWinCount = safeClosed.stream().filter(p -> p.getSide() == PositionSide.SHORT && money(p.getNetPnl()).compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal grossPnl = sum(safeClosed.stream().map(LaplacePaperPositionEntity::getGrossPnl).toList());
        BigDecimal totalEntryFee = sum(safeClosed.stream().map(LaplacePaperPositionEntity::getEntryFee).toList());
        BigDecimal totalExitFee = sum(safeClosed.stream().map(LaplacePaperPositionEntity::getExitFee).toList());
        BigDecimal totalFee = totalEntryFee.add(totalExitFee);
        BigDecimal netPnl = sum(safeClosed.stream().map(LaplacePaperPositionEntity::getNetPnl).toList());
        BigDecimal longNetPnl = sum(safeClosed.stream().filter(p -> p.getSide() == PositionSide.LONG).map(LaplacePaperPositionEntity::getNetPnl).toList());
        BigDecimal shortNetPnl = sum(safeClosed.stream().filter(p -> p.getSide() == PositionSide.SHORT).map(LaplacePaperPositionEntity::getNetPnl).toList());
        BigDecimal bestTrade = safeClosed.stream().map(p -> money(p.getNetPnl())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal worstTrade = safeClosed.stream().map(p -> money(p.getNetPnl())).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        Instant firstTradeTime = safeClosed.stream().map(LaplacePaperPositionEntity::getEntryTime).filter(t -> t != null).min(Instant::compareTo).orElse(null);
        Instant lastTradeTime = safeClosed.stream().map(LaplacePaperPositionEntity::getExitTime).filter(t -> t != null).max(Instant::compareTo).orElse(null);
        return new LaplaceAnalysisSummaryResponse(
                LaplacePaperExecutionService.STRATEGY, LaplacePaperExecutionService.VERSION, tradeCount, safeOpen.size(), tradeCount,
                longTradeCount, shortTradeCount, winCount, lossCount, breakEvenCount, pct(winCount, tradeCount), grossPnl,
                totalEntryFee, totalExitFee, totalFee, netPnl, average(grossPnl, tradeCount), average(netPnl, tradeCount),
                average(sum(safeClosed.stream().map(LaplacePaperPositionEntity::getNetPnlPct).toList()), tradeCount), bestTrade,
                worstTrade, longNetPnl, shortNetPnl, pct(longWinCount, longTradeCount), pct(shortWinCount, shortTradeCount),
                firstTradeTime, lastTradeTime, lastTradeTime);
    }

    private ResponseStatusException unavailable(Exception exception) {
        log.error("LAPLACE_POSITION_DATA_UNAVAILABLE errorType={} errorMessage={}", exception.getClass().getSimpleName(), exception.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Paper position data is temporarily unavailable", exception);
    }

    private String entryExecutionPriceType(PositionSide side) {
        return side == PositionSide.LONG ? "ASK" : "BID";
    }

    private String executionAction(PositionSide side) {
        return side == PositionSide.LONG ? "LONG_OPEN" : "SHORT_OPEN";
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal average(BigDecimal value, long count) {
        return count == 0 ? BigDecimal.ZERO : value.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal pct(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator).multiply(ONE_HUNDRED).divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }
}
