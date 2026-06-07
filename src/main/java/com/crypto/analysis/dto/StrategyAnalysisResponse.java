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
        List<String> observations
) {
}
