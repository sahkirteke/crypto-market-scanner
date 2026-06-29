package com.crypto.persistence.service;

import com.crypto.common.enums.ScanType;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.MarketScanPersistenceMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketScanPersistenceService {
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    private static final int COIN_RESULT_CHUNK_SIZE = 100;
    private static final int COIN_RESULT_MAX_ATTEMPTS = 3;

    private final MarketScanRunRepository marketScanRunRepository;
    private final CoinScanResultRepository coinScanResultRepository;
    private final MarketScanPersistenceMapper marketScanPersistenceMapper;

    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;

    public MarketScanRunEntity saveCompletedScan(MarketScanResult result) {
        MarketScanRunEntity runEntity = marketScanPersistenceMapper.toRunEntity(result, STATUS_COMPLETED);
        MarketScanRunEntity savedRunEntity = marketScanRunRepository.save(runEntity);

        List<CoinScanResultEntity> coinEntities = allCoinResults(result).stream()
                .map(coinResult -> marketScanPersistenceMapper.toCoinEntity(coinResult, savedRunEntity))
                .toList();
        saveCoinResultsInChunks(coinEntities);
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logScanner(Map.of("event", "SCAN_COMPLETED", "scanRunId", savedRunEntity.getId(), "marketRegime", savedRunEntity.getMarketRegime() == null ? "" : savedRunEntity.getMarketRegime().name()));
        }
        return savedRunEntity;
    }

    private void saveCoinResultsInChunks(List<CoinScanResultEntity> coinEntities) {
        if (coinEntities == null || coinEntities.isEmpty()) {
            return;
        }
        int total = coinEntities.size();
        for (int start = 0; start < total; start += COIN_RESULT_CHUNK_SIZE) {
            int end = Math.min(start + COIN_RESULT_CHUNK_SIZE, total);
            saveCoinResultChunkWithRetry(coinEntities.subList(start, end), start / COIN_RESULT_CHUNK_SIZE + 1);
        }
    }

    private void saveCoinResultChunkWithRetry(List<CoinScanResultEntity> chunk, int chunkIndex) {
        int attempt = 1;
        while (true) {
            try {
                coinScanResultRepository.saveAll(chunk);
                return;
            } catch (DataAccessException exception) {
                if (attempt >= COIN_RESULT_MAX_ATTEMPTS) {
                    log.error("SCAN_COIN_RESULTS_CHUNK_SAVE_FAILED chunkIndex={} size={} attempts={} message={}",
                            chunkIndex, chunk.size(), attempt, exception.getMessage());
                    throw exception;
                }
                log.warn("SCAN_COIN_RESULTS_CHUNK_SAVE_RETRY chunkIndex={} size={} attempt={} maxAttempts={} message={}",
                        chunkIndex, chunk.size(), attempt, COIN_RESULT_MAX_ATTEMPTS, exception.getMessage());
                attempt++;
            }
        }
    }

    @Transactional
    public MarketScanRunEntity saveFailedScan(ScanType scanType, Instant scanTimeUtc, String errorMessage) {
        MarketScanRunEntity runEntity = new MarketScanRunEntity();
        runEntity.setScanType(scanType);
        runEntity.setScanTimeUtc(scanTimeUtc);
        runEntity.setStatus(STATUS_FAILED);
        runEntity.setErrorMessage(errorMessage);
        runEntity.setTotalSymbols(0);
        runEntity.setPreFilterPassedCount(0);
        runEntity.setStrongLongCount(0);
        runEntity.setStrongShortCount(0);
        runEntity.setWatchlistCount(0);
        runEntity.setEliminatedCount(0);
        runEntity.setReasonsJson("[]");
        runEntity.setWarningsJson("[]");
        return marketScanRunRepository.save(runEntity);
    }

    private List<CoinScanResult> allCoinResults(MarketScanResult result) {
        List<CoinScanResult> allResults = new ArrayList<>();
        allResults.addAll(safeList(result.getStrongLong()));
        allResults.addAll(safeList(result.getStrongShort()));
        allResults.addAll(safeList(result.getWatchlist()));
        allResults.addAll(safeList(result.getEliminated()));
        return allResults;
    }

    private List<CoinScanResult> safeList(List<CoinScanResult> results) {
        return results == null ? Collections.emptyList() : results;
    }
}
