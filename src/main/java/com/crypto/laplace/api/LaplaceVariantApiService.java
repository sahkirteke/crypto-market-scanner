package com.crypto.laplace.api;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LaplaceVariantApiService {
    private final LaplacePaperPositionRepository invertedTrue;
    private final LaplaceInvertedFalsePositionRepository invertedFalse;
    private final BinanceFuturesClient prices;
    private final LaplacePnlCalculator pnl;

    public List<LaplacePaperPositionResponse> positions(boolean signalInverted, LaplacePositionStatus status) {
        if (signalInverted) return invertedTrue.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, status).stream().map(this::response).toList();
        return invertedFalse.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, status).stream().map(this::response).toList();
    }

    public LaplaceVariantSummaryResponse summary(boolean signalInverted) {
        List<? extends Object> open = signalInverted
                ? invertedTrue.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)
                : invertedFalse.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN);
        List<? extends Object> closed = signalInverted
                ? invertedTrue.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED)
                : invertedFalse.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED);
        BigDecimal closedNet = closed.stream().map(this::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openNet = current(open);
        return new LaplaceVariantSummaryResponse(signalInverted ? "INVERTED_TRUE" : "INVERTED_FALSE",
                open.size(), closed.size(), closedNet, openNet, closedNet.add(openNet));
    }

    private BigDecimal current(List<? extends Object> positions) {
        if (positions.isEmpty()) return BigDecimal.ZERO;
        Map<String, BookTicker> quotes = prices.getAllBookTickers().stream()
                .filter(Objects::nonNull).filter(q -> q.getSymbol() != null)
                .collect(Collectors.toMap(BookTicker::getSymbol, Function.identity(), (a,b) -> a));
        return positions.stream().map(position -> {
            String symbol = symbol(position); PositionSide side = side(position); BookTicker quote = quotes.get(symbol);
            BigDecimal price = quote == null ? null : side == PositionSide.LONG ? quote.getBidPrice() : quote.getAskPrice();
            if (price == null || price.signum() <= 0) throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE:" + symbol);
            return pnl.gross(side, entry(position), price, quantity(position));
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LaplacePaperPositionResponse response(LaplacePaperPositionEntity p) { return new LaplacePaperPositionResponse(p.getId(),p.getSymbol(),p.getSide(),p.getStatus(),p.getEntryTime(),p.getEntryExecutionPrice(),p.getMargin(),p.getQuantity(),p.getNotional(),p.getLeverage(),p.getEntryFee(),p.getExitTime(),p.getExitExecutionPrice(),p.getExitFee(),p.getNetPnl(),p.getExitReason()); }
    private LaplacePaperPositionResponse response(LaplaceInvertedFalsePositionEntity p) { return new LaplacePaperPositionResponse(p.getId(),p.getSymbol(),p.getSide(),p.getStatus(),p.getEntryTime(),p.getEntryExecutionPrice(),p.getMargin(),p.getQuantity(),p.getNotional(),p.getLeverage(),p.getEntryFee(),p.getExitTime(),p.getExitExecutionPrice(),p.getExitFee(),p.getNetPnl(),p.getExitReason()); }
    private BigDecimal net(Object p) { BigDecimal value = p instanceof LaplacePaperPositionEntity x ? x.getNetPnl() : ((LaplaceInvertedFalsePositionEntity)p).getNetPnl(); return value == null ? BigDecimal.ZERO : value; }
    private String symbol(Object p) { return p instanceof LaplacePaperPositionEntity x ? x.getSymbol() : ((LaplaceInvertedFalsePositionEntity)p).getSymbol(); }
    private PositionSide side(Object p) { return p instanceof LaplacePaperPositionEntity x ? x.getSide() : ((LaplaceInvertedFalsePositionEntity)p).getSide(); }
    private BigDecimal entry(Object p) { return p instanceof LaplacePaperPositionEntity x ? x.getEntryExecutionPrice() : ((LaplaceInvertedFalsePositionEntity)p).getEntryExecutionPrice(); }
    private BigDecimal quantity(Object p) { return p instanceof LaplacePaperPositionEntity x ? x.getQuantity() : ((LaplaceInvertedFalsePositionEntity)p).getQuantity(); }
}
