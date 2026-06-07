package com.crypto.persistence.service;

import com.crypto.common.enums.ScanType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"db", "manual-scanner-db"})
@RequiredArgsConstructor
public class MarketScanPersistenceService {
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private final MarketScanRunRepository marketScanRunRepository;
    private final CoinScanResultRepository coinScanResultRepository;
    private final MarketScanPersistenceMapper marketScanPersistenceMapper;

    @Transactional
    public MarketScanRunEntity saveCompletedScan(MarketScanResult result) {
        MarketScanRunEntity runEntity = marketScanPersistenceMapper.toRunEntity(result, STATUS_COMPLETED);
        MarketScanRunEntity savedRunEntity = marketScanRunRepository.save(runEntity);

        List<CoinScanResultEntity> coinEntities = allCoinResults(result).stream()
                .map(coinResult -> marketScanPersistenceMapper.toCoinEntity(coinResult, savedRunEntity))
                .toList();
        coinScanResultRepository.saveAll(coinEntities);
        return savedRunEntity;
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
