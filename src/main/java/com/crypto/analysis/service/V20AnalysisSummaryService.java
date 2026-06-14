package com.crypto.analysis.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import java.math.BigDecimal;
import com.crypto.paper.log.V20PaperJsonlLogService;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.model.V20PaperSummary;
import com.crypto.paper.model.V20PaperSummaryReport;
import com.crypto.paper.service.V20PaperSummaryService;
import com.crypto.paper.service.V20PnlCalculator;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V20AnalysisSummaryService {
    private final PaperPositionRepository paperPositionRepository;
    private final ScannerProperties scannerProperties;
    private final V20PnlCalculator pnlCalculator;
    private final V20PaperSummaryService summaryService;

    @Autowired(required = false)
    private BinanceFuturesClient binanceFuturesClient;

    public Map<String, Object> summary() {
        return summary(null, null, null);
    }

    public Map<String, Object> summary(Integer limit, Instant start, Instant end) {
        List<PaperPositionEntity> closed = paperPositionRepository.findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED).stream()
                .filter(p -> "V20".equalsIgnoreCase(p.getStrategyVersion()))
                .filter(p -> start == null || (p.getClosedAt() != null && !p.getClosedAt().isBefore(start)))
                .filter(p -> end == null || (p.getClosedAt() != null && !p.getClosedAt().isAfter(end)))
                .toList();
        List<PaperPositionEntity> validClosed = closed.stream()
                .filter(summaryService::isValidClosedV20Trade)
                .toList();
        List<PaperPositionEntity> open = paperPositionRepository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN).stream()
                .filter(p -> "V20".equalsIgnoreCase(p.getStrategyVersion()))
                .toList();
        V20PaperSummaryReport report = summaryService.summarize(validClosed);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategyVersion", "V20");
        response.put("timezone", "Europe/Istanbul");
        response.put("generatedAtTr", tr(Instant.now()));
        response.put("paperConfig", paperConfig());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", toMap(report.totalSummary()));
        summary.put("long", toMap(report.longSummary()));
        summary.put("short", toMap(report.shortSummary()));
        response.put("summary", summary);
        response.put("openPositions", openPositions(open));
        response.put("recentClosedPositions", validClosed.stream().limit(limit == null ? 20 : Math.max(0, limit)).map(this::closedItem).toList());
        return response;
    }

    private Map<String, Object> paperConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("marginUsdt", pnlCalculator.marginUsdt());
        map.put("leverage", pnlCalculator.leverage());
        map.put("leveragedNotionalUsdt", pnlCalculator.leveragedNotionalUsdt());
        map.put("feeMode", pnlCalculator.feeMode());
        map.put("feeRate", pnlCalculator.feeRate());
        map.put("slippagePct", pnlCalculator.slippagePct());
        map.put("tpPct", scannerProperties.getSingleTpSl().getLongTpPct());
        map.put("slPct", scannerProperties.getSingleTpSl().getLongSlPct());
        return map;
    }

    private Map<String, Object> toMap(V20PaperSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tradeCount", s.tradeCount());
        map.put("winCount", s.winCount());
        map.put("lossCount", s.lossCount());
        map.put("winRate", s.winRate());
        map.put("takeProfitCount", s.takeProfitCount());
        map.put("stopLossCount", s.stopLossCount());
        map.put("unleveragedTotalFeeUsdt", s.unleveragedTotalFeeUsdt());
        map.put("unleveragedNetPnlUsdt", s.unleveragedNetPnlUsdt());
        map.put("unleveragedAvgNetPnlUsdt", s.unleveragedAvgNetPnlUsdt());
        map.put("unleveragedNetPnlPctOnMargin", s.unleveragedNetPnlPctOnMargin());
        map.put("leveragedTotalFeeUsdt", s.leveragedTotalFeeUsdt());
        map.put("leveragedNetPnlUsdt", s.leveragedNetPnlUsdt());
        map.put("leveragedAvgNetPnlUsdt", s.leveragedAvgNetPnlUsdt());
        map.put("leveragedNetPnlPctOnMargin", s.leveragedNetPnlPctOnMargin());
        return map;
    }

    private Map<String, Object> openPositions(List<PaperPositionEntity> open) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", open.size());
        map.put("longCount", open.stream().filter(p -> p.getSide() == PositionSide.LONG).count());
        map.put("shortCount", open.stream().filter(p -> p.getSide() == PositionSide.SHORT).count());
        map.put("items", open.stream().map(this::openItem).toList());
        return map;
    }

    private Map<String, Object> openItem(PaperPositionEntity p) {
        Map<String, Object> map = baseItem(p);
        map.put("entryTimeTr", tr(p.getOpenedAt()));
        map.put("takeProfitPrice", p.getTakeProfitPrice());
        map.put("stopLossPrice", p.getStopLossPrice());
        V20PnlResultView unrealized = unrealized(p);
        map.put("unrealizedRawPnlPct", unrealized == null ? p.getRawUnrealizedPnlPct() : unrealized.rawPnlPct());
        map.put("unrealizedUnleveragedNetPnlUsdt", unrealized == null ? null : unrealized.unleveragedNetPnlUsdt());
        map.put("unrealizedLeveragedNetPnlUsdt", unrealized == null ? null : unrealized.leveragedNetPnlUsdt());
        map.put("unrealizedLeveragedNetPnlPct", unrealized == null ? null : unrealized.leveragedNetPnlPct());
        return map;
    }


    private V20PnlResultView unrealized(PaperPositionEntity p) {
        try {
            BigDecimal mid = latestMidPrice(p.getSymbol());
            if (mid == null || p.getEntryPrice() == null || p.getSide() == null) {
                return null;
            }
            var pnl = pnlCalculator.calculate(p.getSide(), p.getEntryPrice(), mid);
            return new V20PnlResultView(pnl.rawPnlPct(), pnl.unleveragedNetPnlUsdt(), pnl.leveragedNetPnlUsdt(), pnl.leveragedNetPnlPct());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal latestMidPrice(String symbol) {
        if (binanceFuturesClient == null || symbol == null) {
            return null;
        }
        List<BookTicker> tickers = binanceFuturesClient.getAllBookTickers();
        return tickers == null ? null : tickers.stream()
                .filter(t -> t != null && symbol.equals(t.getSymbol()))
                .map(BookTicker::getMidPrice)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);
    }

    private record V20PnlResultView(BigDecimal rawPnlPct, BigDecimal unleveragedNetPnlUsdt, BigDecimal leveragedNetPnlUsdt, BigDecimal leveragedNetPnlPct) {}

    private Map<String, Object> closedItem(PaperPositionEntity p) {
        Map<String, Object> map = baseItem(p);
        map.put("entryTimeTr", tr(p.getOpenedAt()));
        map.put("exitTimeTr", tr(p.getClosedAt()));
        map.put("exitReason", p.getExitReason());
        map.put("exitPrice", p.getExitPrice());
        map.put("unleveragedNetPnlUsdt", p.getUnleveragedNetPnlUsdt());
        map.put("leveragedNetPnlUsdt", p.getLeveragedNetPnlUsdt());
        map.put("leveragedNetPnlPct", p.getLeveragedNetPnlPct());
        return map;
    }

    private Map<String, Object> baseItem(PaperPositionEntity p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("positionId", p.getId());
        map.put("symbol", p.getSymbol());
        map.put("side", p.getSide() == null ? null : p.getSide().name());
        map.put("entryPrice", p.getEntryPrice());
        map.put("marginUsdt", p.getMarginUsdt());
        map.put("leverage", p.getLeverage());
        map.put("leveragedNotionalUsdt", p.getLeveragedNotionalUsdt());
        map.put("quantity", p.getQuantity());
        return map;
    }

    private String tr(Instant instant) { return instant == null ? null : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(V20PaperJsonlLogService.ISTANBUL_ZONE).format(instant); }
}
