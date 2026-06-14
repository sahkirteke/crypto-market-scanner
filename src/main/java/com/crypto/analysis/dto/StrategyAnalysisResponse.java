package com.crypto.analysis.dto;

import java.util.List;

public record StrategyAnalysisResponse(
        StrategyPerformanceSummaryResponse summary,
        List<DirectionPerformanceResponse> byDirection,
        List<SymbolPerformanceResponse> topSymbols,
        List<SymbolPerformanceResponse> worstSymbols,
        List<ClassificationPerformanceResponse> byClassification,
        List<RiskPerformanceResponse> byRiskLevel,
        List<ExitReasonPerformanceResponse> byExitReason,
        List<PerformanceBreakdownResponse> byFourHourAlignment,
        List<PerformanceBreakdownResponse> bySideAndFourHourAlignment,
        List<PerformanceBreakdownResponse> byEntryPriorityBucket,
        RMetricsResponse rMetrics,
        List<PerformanceBreakdownResponse> bySymbolCooldownImpact,
        List<String> observations
) {
}
