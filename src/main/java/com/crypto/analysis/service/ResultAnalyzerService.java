package com.crypto.analysis.service;

import com.crypto.analysis.dto.ClassificationPerformanceResponse;
import com.crypto.analysis.dto.DirectionPerformanceResponse;
import com.crypto.analysis.dto.ExitReasonPerformanceResponse;
import com.crypto.analysis.dto.RiskPerformanceResponse;
import com.crypto.analysis.dto.PerformanceBreakdownResponse;
import com.crypto.analysis.dto.RMetricsResponse;
import com.crypto.analysis.dto.StrategyAnalysisResponse;
import com.crypto.analysis.dto.StrategyPerformanceSummaryResponse;
import com.crypto.analysis.dto.SymbolPerformanceResponse;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultAnalyzerService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final int SCALE = 8;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal OUTPERFORMANCE_THRESHOLD = new BigDecimal("10");
    private static final BigDecimal HIGH_ADVERSE_MOVE_THRESHOLD = new BigDecimal("-1.5");

    private final PaperPositionRepository paperPositionRepository;

    public StrategyAnalysisResponse analyzeAllClosedTrades() {
        return analyze(paperPositionRepository.findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED));
    }

    public StrategyAnalysisResponse analyzeClosedTradesBetween(Instant start, Instant end) {
        return analyze(paperPositionRepository.findByStatusAndClosedAtBetweenOrderByClosedAtDesc(
                PaperPositionStatus.CLOSED,
                start,
                end
        ));
    }

    public StrategyAnalysisResponse analyzeLastClosedTrades(int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<PaperPositionEntity> trades = paperPositionRepository
                .findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED, PageRequest.of(0, normalizedLimit))
                .getContent();
        return analyze(trades);
    }

    public StrategyAnalysisResponse analyze(List<PaperPositionEntity> trades) {
        List<PaperPositionEntity> closedTrades = trades == null ? List.of() : trades.stream()
                .filter(Objects::nonNull)
                .filter(trade -> trade.getStatus() == null || PaperPositionStatus.CLOSED == trade.getStatus())
                .toList();
        log.info("RESULT_ANALYSIS_STARTED tradeCount={}", closedTrades.size());

        StrategyPerformanceSummaryResponse summary = buildSummary(closedTrades);
        List<DirectionPerformanceResponse> byDirection = buildDirectionBreakdown(closedTrades);
        List<SymbolPerformanceResponse> symbolPerformance = buildSymbolPerformance(closedTrades);
        List<SymbolPerformanceResponse> topSymbols = symbolPerformance.stream()
                .sorted(Comparator.comparing(SymbolPerformanceResponse::totalRealizedPnlUsdt).reversed())
                .limit(10)
                .toList();
        List<SymbolPerformanceResponse> worstSymbols = symbolPerformance.stream()
                .sorted(Comparator.comparing(SymbolPerformanceResponse::totalRealizedPnlUsdt))
                .limit(10)
                .toList();
        List<ClassificationPerformanceResponse> byClassification = buildSimpleBreakdown(
                closedTrades,
                this::classificationKey,
                ClassificationPerformanceResponse::new
        );
        List<RiskPerformanceResponse> byRiskLevel = buildSimpleBreakdown(
                closedTrades,
                this::riskLevelKey,
                RiskPerformanceResponse::new
        );
        List<ExitReasonPerformanceResponse> byExitReason = buildSimpleBreakdown(
                closedTrades,
                this::exitReasonKey,
                ExitReasonPerformanceResponse::new
        );
        List<PerformanceBreakdownResponse> byFourHourAlignment = buildPerformanceBreakdown(closedTrades, this::fourHourAlignmentKey);
        List<PerformanceBreakdownResponse> bySideAndFourHourAlignment = buildPerformanceBreakdown(closedTrades, this::sideAndFourHourAlignmentKey);
        List<PerformanceBreakdownResponse> byEntryPriorityBucket = buildPerformanceBreakdown(closedTrades, this::entryPriorityBucketKey);
        RMetricsResponse rMetrics = buildRMetrics(closedTrades);
        List<PerformanceBreakdownResponse> bySymbolCooldownImpact = buildPerformanceBreakdown(closedTrades, this::cooldownImpactKey);
        List<String> observations = buildObservations(
                closedTrades,
                summary,
                byDirection,
                byExitReason
        );

        log.info(
                "RESULT_ANALYSIS_DONE totalTrades={} winRate={} totalPnl={}",
                summary.totalTrades(),
                summary.winRatePct(),
                summary.totalRealizedPnlUsdt()
        );
        return new StrategyAnalysisResponse(
                summary,
                byDirection,
                topSymbols,
                worstSymbols,
                byClassification,
                byRiskLevel,
                byExitReason,
                byFourHourAlignment,
                bySideAndFourHourAlignment,
                byEntryPriorityBucket,
                rMetrics,
                bySymbolCooldownImpact,
                observations
        );
    }

    private StrategyPerformanceSummaryResponse buildSummary(List<PaperPositionEntity> trades) {
        int winCount = countWins(trades);
        int lossCount = countLosses(trades);
        int flatCount = countFlats(trades);
        List<BigDecimal> pnlPctValues = values(trades, PaperPositionEntity::getRealizedPnlPct);

        return new StrategyPerformanceSummaryResponse(
                trades.size(),
                trades.size(),
                Math.toIntExact(paperPositionRepository.countByStatus(PaperPositionStatus.OPEN)),
                winCount,
                lossCount,
                flatCount,
                pct(winCount, winCount + lossCount),
                sum(values(trades, PaperPositionEntity::getRealizedPnlUsdt)),
                average(pnlPctValues),
                average(pnlPctValues.stream().filter(this::isPositive).toList()),
                average(pnlPctValues.stream().filter(this::isNegative).toList()),
                pnlPctValues.stream().max(BigDecimal::compareTo).map(this::scale).orElse(ZERO),
                pnlPctValues.stream().min(BigDecimal::compareTo).map(this::scale).orElse(ZERO),
                average(values(trades, PaperPositionEntity::getMaxFavorableMovePct)),
                average(values(trades, PaperPositionEntity::getMaxAdverseMovePct)),
                averageInteger(valuesInteger(trades, PaperPositionEntity::getMinutesHeld)),
                averageInteger(valuesInteger(trades, PaperPositionEntity::getBarsHeld)),
                IstanbulTimeUtil.format(trades.stream().map(PaperPositionEntity::getOpenedAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(null)),
                IstanbulTimeUtil.format(trades.stream().map(PaperPositionEntity::getClosedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null))
        );
    }

    private List<DirectionPerformanceResponse> buildDirectionBreakdown(List<PaperPositionEntity> trades) {
        return List.of(PositionSide.LONG, PositionSide.SHORT).stream()
                .map(side -> {
                    List<PaperPositionEntity> sideTrades = trades.stream()
                            .filter(trade -> side == trade.getSide())
                            .toList();
                    int winCount = countWins(sideTrades);
                    int lossCount = countLosses(sideTrades);
                    return new DirectionPerformanceResponse(
                            side.name(),
                            sideTrades.size(),
                            winCount,
                            lossCount,
                            countFlats(sideTrades),
                            pct(winCount, winCount + lossCount),
                            sum(values(sideTrades, PaperPositionEntity::getRealizedPnlUsdt)),
                            average(values(sideTrades, PaperPositionEntity::getRealizedPnlPct)),
                            average(values(sideTrades, PaperPositionEntity::getMaxFavorableMovePct)),
                            average(values(sideTrades, PaperPositionEntity::getMaxAdverseMovePct)),
                            averageInteger(valuesInteger(sideTrades, PaperPositionEntity::getMinutesHeld))
                    );
                })
                .toList();
    }

    private List<SymbolPerformanceResponse> buildSymbolPerformance(List<PaperPositionEntity> trades) {
        return trades.stream()
                .collect(Collectors.groupingBy(trade -> valueOrUnknown(trade.getSymbol())))
                .entrySet()
                .stream()
                .map(entry -> buildSymbolPerformance(entry.getKey(), entry.getValue()))
                .toList();
    }

    private SymbolPerformanceResponse buildSymbolPerformance(String symbol, List<PaperPositionEntity> trades) {
        int winCount = countWins(trades);
        int lossCount = countLosses(trades);
        List<BigDecimal> pnlPctValues = values(trades, PaperPositionEntity::getRealizedPnlPct);
        return new SymbolPerformanceResponse(
                symbol,
                trades.size(),
                winCount,
                lossCount,
                countFlats(trades),
                pct(winCount, winCount + lossCount),
                sum(values(trades, PaperPositionEntity::getRealizedPnlUsdt)),
                average(pnlPctValues),
                pnlPctValues.stream().max(BigDecimal::compareTo).map(this::scale).orElse(ZERO),
                pnlPctValues.stream().min(BigDecimal::compareTo).map(this::scale).orElse(ZERO),
                average(values(trades, PaperPositionEntity::getMaxFavorableMovePct)),
                average(values(trades, PaperPositionEntity::getMaxAdverseMovePct))
        );
    }

    private List<PerformanceBreakdownResponse> buildPerformanceBreakdown(
            List<PaperPositionEntity> trades,
            Function<PaperPositionEntity, String> keyMapper
    ) {
        return trades.stream()
                .collect(Collectors.groupingBy(keyMapper))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<PaperPositionEntity> groupTrades = entry.getValue();
                    int winCount = countWins(groupTrades);
                    int lossCount = countLosses(groupTrades);
                    return new PerformanceBreakdownResponse(
                            entry.getKey(),
                            groupTrades.size(),
                            winCount,
                            lossCount,
                            pct(winCount, winCount + lossCount),
                            sum(values(groupTrades, PaperPositionEntity::getRealizedPnlUsdt)),
                            average(values(groupTrades, PaperPositionEntity::getRealizedPnlPct)),
                            average(values(groupTrades, PaperPositionEntity::getMaxFavorableMovePct)),
                            average(values(groupTrades, PaperPositionEntity::getMaxAdverseMovePct))
                    );
                })
                .toList();
    }

    private RMetricsResponse buildRMetrics(List<PaperPositionEntity> trades) {
        List<BigDecimal> mfeR = trades.stream().map(this::mfeInR).filter(Objects::nonNull).toList();
        List<BigDecimal> maeR = trades.stream().map(this::maeInR).filter(Objects::nonNull).toList();
        return new RMetricsResponse(
                average(mfeR),
                average(maeR),
                (int) trades.stream().filter(t -> isStop(t) && mfeInR(t) != null && mfeInR(t).compareTo(new BigDecimal("0.8")) >= 0).count(),
                (int) trades.stream().filter(t -> isStop(t) && mfeInR(t) != null && mfeInR(t).compareTo(BigDecimal.ONE) >= 0).count(),
                (int) trades.stream().filter(t -> isStop(t) && isPositive(t.getMaxFavorableMovePct())).count()
        );
    }

    private <T> List<T> buildSimpleBreakdown(
            List<PaperPositionEntity> trades,
            Function<PaperPositionEntity, String> keyMapper,
            SimpleBreakdownFactory<T> factory
    ) {
        return trades.stream()
                .collect(Collectors.groupingBy(keyMapper))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<PaperPositionEntity> groupTrades = entry.getValue();
                    int winCount = countWins(groupTrades);
                    int lossCount = countLosses(groupTrades);
                    return factory.create(
                            entry.getKey(),
                            groupTrades.size(),
                            winCount,
                            lossCount,
                            pct(winCount, winCount + lossCount),
                            sum(values(groupTrades, PaperPositionEntity::getRealizedPnlUsdt)),
                            average(values(groupTrades, PaperPositionEntity::getRealizedPnlPct))
                    );
                })
                .toList();
    }

    private List<String> buildObservations(
            List<PaperPositionEntity> trades,
            StrategyPerformanceSummaryResponse summary,
            List<DirectionPerformanceResponse> byDirection,
            List<ExitReasonPerformanceResponse> byExitReason
    ) {
        List<String> observations = new ArrayList<>();
        if (summary.totalTrades() == 0) {
            observations.add("No closed paper trades found.");
        }
        if (summary.totalTrades() < 20) {
            observations.add("Sample size is low; analysis may not be reliable.");
        }
        if (trades.stream().anyMatch(trade -> trade.getRealizedPnlPct() == null)) {
            observations.add("Some closed trades have null realizedPnlPct");
        }
        if (summary.winRatePct().compareTo(new BigDecimal("60")) >= 0) {
            observations.add("Overall win rate is strong.");
        }
        if (summary.winRatePct().compareTo(new BigDecimal("45")) < 0 && summary.totalTrades() >= 20) {
            observations.add("Overall win rate is weak.");
        }
        if (isPositive(summary.totalRealizedPnlUsdt())) {
            observations.add("Total realized PnL is positive.");
        }
        if (isNegative(summary.totalRealizedPnlUsdt())) {
            observations.add("Total realized PnL is negative.");
        }

        DirectionPerformanceResponse longPerformance = findDirection(byDirection, PositionSide.LONG);
        DirectionPerformanceResponse shortPerformance = findDirection(byDirection, PositionSide.SHORT);
        if (longPerformance.winRatePct().compareTo(shortPerformance.winRatePct().add(OUTPERFORMANCE_THRESHOLD)) > 0) {
            observations.add("LONG signals currently outperform SHORT signals.");
        }
        if (shortPerformance.winRatePct().compareTo(longPerformance.winRatePct().add(OUTPERFORMANCE_THRESHOLD)) > 0) {
            observations.add("SHORT signals currently outperform LONG signals.");
        }

        int stopLossCount = exitReasonCount(byExitReason, "STOP_LOSS");
        int takeProfitCount = exitReasonCount(byExitReason, "TAKE_PROFIT");
        if (stopLossCount > takeProfitCount * 2) {
            observations.add("Stop-loss exits dominate; entry filters may be too loose or TP/SL ratio may need review.");
        }
        if (summary.avgMaxAdverseMovePct().compareTo(HIGH_ADVERSE_MOVE_THRESHOLD) <= 0) {
            observations.add("Average adverse move is high; stop-loss or entry timing should be reviewed.");
        }
        return observations;
    }

    private DirectionPerformanceResponse findDirection(List<DirectionPerformanceResponse> responses, PositionSide side) {
        return responses.stream()
                .filter(response -> side.name().equals(response.side()))
                .findFirst()
                .orElseThrow();
    }

    private int exitReasonCount(List<ExitReasonPerformanceResponse> responses, String exitReason) {
        return responses.stream()
                .filter(response -> exitReason.equals(response.exitReason()))
                .map(ExitReasonPerformanceResponse::totalTrades)
                .findFirst()
                .orElse(0);
    }

    private int countWins(List<PaperPositionEntity> trades) {
        return (int) trades.stream().filter(trade -> isPositive(trade.getRealizedPnlPct())).count();
    }

    private int countLosses(List<PaperPositionEntity> trades) {
        return (int) trades.stream().filter(trade -> isNegative(trade.getRealizedPnlPct())).count();
    }

    private int countFlats(List<PaperPositionEntity> trades) {
        return (int) trades.stream()
                .filter(trade -> trade.getRealizedPnlPct() == null || BigDecimal.ZERO.compareTo(trade.getRealizedPnlPct()) == 0)
                .count();
    }

    private List<BigDecimal> values(List<PaperPositionEntity> trades, Function<PaperPositionEntity, BigDecimal> mapper) {
        return trades.stream().map(mapper).filter(Objects::nonNull).toList();
    }

    private List<Integer> valuesInteger(List<PaperPositionEntity> trades, Function<PaperPositionEntity, Integer> mapper) {
        return trades.stream().map(mapper).filter(Objects::nonNull).toList();
    }

    private BigDecimal averageInteger(List<Integer> values) {
        return average(values.stream().map(BigDecimal::new).toList());
    }

    private BigDecimal bd(BigDecimal value) {
        return value == null ? ZERO : scale(value);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return ZERO;
        }
        return sum(values).divide(new BigDecimal(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return ZERO;
        }
        return scale(values.stream().filter(Objects::nonNull).map(this::bd).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal pct(int numerator, int denominator) {
        if (denominator <= 0) {
            return ZERO;
        }
        return new BigDecimal(numerator)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(denominator), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String fourHourAlignmentKey(PaperPositionEntity trade) {
        return trade.getFourHourAlignment() == null ? "NEUTRAL_4H" : trade.getFourHourAlignment().name();
    }

    private String sideAndFourHourAlignmentKey(PaperPositionEntity trade) {
        String side = trade.getSide() == null ? "UNKNOWN" : trade.getSide().name();
        String align = fourHourAlignmentKey(trade).replace("AGAINST_4H_TREND", "AGAINST_4H");
        return side + "_" + align;
    }

    private String entryPriorityBucketKey(PaperPositionEntity trade) {
        Integer score = trade.getEntryPriorityScore();
        if (score == null) return "below65";
        if (score >= 90) return "90+";
        if (score >= 80) return "80-89";
        if (score >= 70) return "70-79";
        if (score >= 65) return "65-69";
        return "below65";
    }

    private String cooldownImpactKey(PaperPositionEntity trade) {
        return Boolean.TRUE.equals(trade.getCooldownPenaltyApplied()) ? "cooldownPenaltyApplied=true" : "cooldownPenaltyApplied=false";
    }

    private BigDecimal mfeInR(PaperPositionEntity trade) {
        return rValue(trade.getMaxFavorableMovePct(), trade);
    }

    private BigDecimal maeInR(PaperPositionEntity trade) {
        return rValue(trade.getMaxAdverseMovePct(), trade);
    }

    private BigDecimal rValue(BigDecimal movePct, PaperPositionEntity trade) {
        if (movePct == null || trade.getEntryPrice() == null || trade.getRiskPerUnit() == null || trade.getRiskPerUnit().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal riskPct = trade.getRiskPerUnit().divide(trade.getEntryPrice(), SCALE, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        if (riskPct.compareTo(BigDecimal.ZERO) == 0) return null;
        return movePct.divide(riskPct, SCALE, RoundingMode.HALF_UP);
    }

    private boolean isStop(PaperPositionEntity trade) {
        return "STOP_LOSS".equals(trade.getExitReason());
    }

    private String classificationKey(PaperPositionEntity trade) {
        return trade.getSourceClassification() == null ? "UNKNOWN" : trade.getSourceClassification().name();
    }

    private String riskLevelKey(PaperPositionEntity trade) {
        return trade.getRiskLevel() == null ? "UNKNOWN" : trade.getRiskLevel().name();
    }

    private String exitReasonKey(PaperPositionEntity trade) {
        return valueOrUnknown(trade.getExitReason());
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    @FunctionalInterface
    private interface SimpleBreakdownFactory<T> {
        T create(
                String key,
                Integer totalTrades,
                Integer winCount,
                Integer lossCount,
                BigDecimal winRatePct,
                BigDecimal totalRealizedPnlUsdt,
                BigDecimal avgRealizedPnlPct
        );
    }
}
