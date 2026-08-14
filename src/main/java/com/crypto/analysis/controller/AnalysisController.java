package com.crypto.analysis.controller;

import com.crypto.analysis.dto.StrategyAnalysisResponse;
import com.crypto.analysis.service.ResultAnalyzerService;
import com.crypto.analysis.service.ForwardMetricsService;
import com.crypto.api.exception.BadRequestException;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.laplace.api.LaplacePaperApiService;
import com.crypto.laplace.api.LaplaceDualSummaryService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {
    private final ResultAnalyzerService resultAnalyzerService;
    private final ForwardMetricsService forwardMetricsService;
    private final LaplaceDualSummaryService laplaceDualSummaryService;
    @Autowired(required = false)
    private LaplacePaperApiService laplacePaperApiService;

    @GetMapping("/summary")
    public Object getSummary(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        if ((start == null && end == null && limit == null) && laplacePaperApiService != null && laplacePaperApiService.isLaplaceActive()) {
            log.info("ANALYSIS_SUMMARY_REQUEST strategy=LAPLACE_KERNEL_REGRESSION_30M");
            return laplaceDualSummaryService.summaries();
        }
        if (start != null || end != null) {
            if (start == null || end == null) {
                throw new BadRequestException("Both start and end must be provided for range analysis");
            }
            Instant startInstant = parseInstant(start, "start");
            Instant endInstant = parseInstant(end, "end");
            log.info("ANALYSIS_SUMMARY_REQUEST limit={} start={} end={}", limit, IstanbulTimeUtil.format(startInstant), IstanbulTimeUtil.format(endInstant));
            return resultAnalyzerService.analyzeClosedTradesBetween(startInstant, endInstant);
        }
        log.info("ANALYSIS_SUMMARY_REQUEST limit={}", limit);
        if (limit != null) {
            return resultAnalyzerService.analyzeLastClosedTrades(limit);
        }
        return resultAnalyzerService.analyzeAllClosedTrades();
    }

    @PostMapping("/calculate-forward-metrics")
    public java.util.Map<String, Object> calculateForwardMetrics() {
        var saved = forwardMetricsService.calculateMissingMetrics();
        return java.util.Map.of("calculated", saved.size());
    }

    @GetMapping("/last")
    public StrategyAnalysisResponse getLast(@RequestParam(defaultValue = "100") int limit) {
        log.info("ANALYSIS_LAST_REQUEST limit={}", limit);
        return resultAnalyzerService.analyzeLastClosedTrades(limit);
    }

    @GetMapping("/range")
    public StrategyAnalysisResponse getRange(
            @RequestParam String start,
            @RequestParam String end
    ) {
        Instant startInstant = parseInstant(start, "start");
        Instant endInstant = parseInstant(end, "end");
        log.info("ANALYSIS_RANGE_REQUEST start={} end={}", IstanbulTimeUtil.format(startInstant), IstanbulTimeUtil.format(endInstant));
        return resultAnalyzerService.analyzeClosedTradesBetween(startInstant, endInstant);
    }

    private Instant parseInstant(String value, String fieldName) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid " + fieldName + " instant: " + value);
        }
    }
}
