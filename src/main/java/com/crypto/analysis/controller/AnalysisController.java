package com.crypto.analysis.controller;

import com.crypto.analysis.dto.StrategyAnalysisResponse;
import com.crypto.analysis.service.ResultAnalyzerService;
import com.crypto.api.exception.BadRequestException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {
    private final ResultAnalyzerService resultAnalyzerService;

    @GetMapping("/summary")
    public StrategyAnalysisResponse getSummary(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        log.info("ANALYSIS_SUMMARY_REQUEST limit={} start={} end={}", limit, start, end);
        if (start != null || end != null) {
            if (start == null || end == null) {
                throw new BadRequestException("Both start and end must be provided for range analysis");
            }
            return resultAnalyzerService.analyzeClosedTradesBetween(parseInstant(start, "start"), parseInstant(end, "end"));
        }
        if (limit != null) {
            return resultAnalyzerService.analyzeLastClosedTrades(limit);
        }
        return resultAnalyzerService.analyzeAllClosedTrades();
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
        log.info("ANALYSIS_RANGE_REQUEST start={} end={}", start, end);
        return resultAnalyzerService.analyzeClosedTradesBetween(parseInstant(start, "start"), parseInstant(end, "end"));
    }

    private Instant parseInstant(String value, String fieldName) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid " + fieldName + " instant: " + value);
        }
    }
}
