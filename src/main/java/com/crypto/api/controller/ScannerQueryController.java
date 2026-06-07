package com.crypto.api.controller;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.api.dto.LatestScanResponse;
import com.crypto.api.dto.MarketScanRunResponse;
import com.crypto.api.dto.ScanDetailResponse;
import com.crypto.api.exception.BadRequestException;
import com.crypto.api.service.ScannerQueryService;
import com.crypto.common.enums.CoinClassification;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
public class ScannerQueryController {
    private final ScannerQueryService scannerQueryService;

    @GetMapping("/latest")
    public LatestScanResponse getLatest() {
        return scannerQueryService.getLatestCompletedScan();
    }

    @GetMapping("/runs")
    public List<MarketScanRunResponse> getRuns(@RequestParam(defaultValue = "20") int limit) {
        return scannerQueryService.getRecentScans(limit);
    }

    @GetMapping("/runs/{scanRunId}")
    public ScanDetailResponse getScanDetail(
            @PathVariable Long scanRunId,
            @RequestParam(defaultValue = "false") boolean includeEliminated
    ) {
        return scannerQueryService.getScanDetail(scanRunId, includeEliminated);
    }

    @GetMapping("/runs/{scanRunId}/coins")
    public List<CoinScanResultResponse> getCoins(
            @PathVariable Long scanRunId,
            @RequestParam String classification,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return scannerQueryService.getCoinsByClassification(scanRunId, parseClassification(classification), limit);
    }

    @GetMapping("/symbols/{symbol}/history")
    public List<CoinScanResultResponse> getSymbolHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return scannerQueryService.getSymbolHistory(symbol, limit);
    }

    private CoinClassification parseClassification(String classification) {
        if (classification == null || classification.isBlank()) {
            throw new BadRequestException("classification is required");
        }
        String normalizedClassification = classification.toUpperCase(Locale.ROOT);
        try {
            return CoinClassification.valueOf(normalizedClassification);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid classification: " + classification);
        }
    }
}
