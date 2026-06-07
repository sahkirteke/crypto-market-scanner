package com.crypto.api.service;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.api.dto.LatestScanResponse;
import com.crypto.api.dto.MarketScanRunResponse;
import com.crypto.api.dto.ScanDetailResponse;
import com.crypto.api.exception.ResourceNotFoundException;
import com.crypto.api.mapper.ScannerApiMapper;
import com.crypto.common.enums.CoinClassification;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScannerQueryService {
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final int DEFAULT_RECENT_SCAN_LIMIT = 20;
    private static final int MAX_RECENT_SCAN_LIMIT = 100;
    private static final int DEFAULT_COIN_LIMIT = 50;
    private static final int MAX_COIN_LIMIT = 500;
    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final MarketScanRunRepository marketScanRunRepository;
    private final CoinScanResultRepository coinScanResultRepository;
    private final ScannerApiMapper scannerApiMapper;

    public LatestScanResponse getLatestCompletedScan() {
        log.info("SCANNER_API_LATEST_REQUEST");
        MarketScanRunEntity scanRun = marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc(COMPLETED_STATUS)
                .orElseThrow(() -> new ResourceNotFoundException("Completed scanner run not found"));

        return new LatestScanResponse(
                scannerApiMapper.toRunResponse(scanRun),
                getCoins(scanRun.getId(), CoinClassification.STRONG_LONG),
                getCoins(scanRun.getId(), CoinClassification.STRONG_SHORT),
                getCoins(scanRun.getId(), CoinClassification.WATCHLIST)
        );
    }

    public List<MarketScanRunResponse> getRecentScans(int limit) {
        int sanitizedLimit = sanitizeLimit(limit, DEFAULT_RECENT_SCAN_LIMIT, MAX_RECENT_SCAN_LIMIT);
        log.info("SCANNER_API_RUNS_REQUEST limit={}", sanitizedLimit);
        Pageable pageable = PageRequest.of(0, sanitizedLimit);
        return marketScanRunRepository.findAllByOrderByScanTimeUtcDesc(pageable)
                .getContent()
                .stream()
                .map(scannerApiMapper::toRunResponse)
                .toList();
    }

    public ScanDetailResponse getScanDetail(Long scanRunId, boolean includeEliminated) {
        log.info("SCANNER_API_DETAIL_REQUEST scanRunId={} includeEliminated={}", scanRunId, includeEliminated);
        MarketScanRunEntity scanRun = marketScanRunRepository.findById(scanRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner run not found: " + scanRunId));

        List<CoinScanResultResponse> eliminated = includeEliminated
                ? getCoins(scanRunId, CoinClassification.ELIMINATED)
                : Collections.emptyList();

        return new ScanDetailResponse(
                scannerApiMapper.toRunResponse(scanRun),
                getCoins(scanRunId, CoinClassification.STRONG_LONG),
                getCoins(scanRunId, CoinClassification.STRONG_SHORT),
                getCoins(scanRunId, CoinClassification.WATCHLIST),
                eliminated
        );
    }

    public List<CoinScanResultResponse> getCoinsByClassification(
            Long scanRunId,
            CoinClassification classification,
            int limit
    ) {
        int sanitizedLimit = sanitizeLimit(limit, DEFAULT_COIN_LIMIT, MAX_COIN_LIMIT);
        log.info("SCANNER_API_COINS_REQUEST scanRunId={} classification={} limit={}", scanRunId, classification, sanitizedLimit);
        ensureScanExists(scanRunId);
        return scannerApiMapper.toCoinResponseList(coinScanResultRepository
                .findByScanRun_IdAndClassificationOrderByScoreDesc(scanRunId, classification, PageRequest.of(0, sanitizedLimit))
                .getContent());
    }

    public List<CoinScanResultResponse> getSymbolHistory(String symbol, int limit) {
        int sanitizedLimit = sanitizeLimit(limit, DEFAULT_HISTORY_LIMIT, MAX_HISTORY_LIMIT);
        String normalizedSymbol = symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
        log.info("SCANNER_API_SYMBOL_HISTORY_REQUEST symbol={} limit={}", normalizedSymbol, sanitizedLimit);
        return scannerApiMapper.toCoinResponseList(coinScanResultRepository
                .findBySymbolOrderByCreatedAtDesc(normalizedSymbol, PageRequest.of(0, sanitizedLimit))
                .getContent());
    }

    private List<CoinScanResultResponse> getCoins(Long scanRunId, CoinClassification classification) {
        List<CoinScanResultEntity> entities = coinScanResultRepository
                .findByScanRun_IdAndClassificationOrderByScoreDesc(scanRunId, classification);
        return scannerApiMapper.toCoinResponseList(entities);
    }

    private void ensureScanExists(Long scanRunId) {
        if (!marketScanRunRepository.existsById(scanRunId)) {
            throw new ResourceNotFoundException("Scanner run not found: " + scanRunId);
        }
    }

    private int sanitizeLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }
}
