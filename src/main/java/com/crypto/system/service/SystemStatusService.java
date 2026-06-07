package com.crypto.system.service;

import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.system.dto.DbConsistencyResponse;
import com.crypto.system.dto.SystemStatusResponse;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemStatusService {
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final ScannerProperties scannerProperties;
    private final Environment environment;
    private final MarketScanRunRepository marketScanRunRepository;
    private final CoinScanResultRepository coinScanResultRepository;
    private final PaperPositionRepository paperPositionRepository;

    @Value("${spring.application.name:crypto-market-scanner}")
    private String appName;

    public SystemStatusResponse getStatus() {
        return new SystemStatusResponse(
                "UP",
                Instant.now(),
                scannerProperties.getScheduler().getEnabled(),
                scannerProperties.getPaperAuto().getEnabled(),
                scannerProperties.getPaperAuto().getOpenAfterScan(),
                scannerProperties.getPaperAuto().getEvaluateEnabled(),
                scannerProperties.getPaperAuto().getEvaluateCron(),
                scannerProperties.getPaper().getEnabled(),
                scannerProperties.getPaperExit().getEnabled(),
                scannerProperties.getSafeMode(),
                scannerProperties.getScheduler().getZone(),
                activeProfiles(),
                appName
        );
    }

    public DbConsistencyResponse getDbConsistency() {
        MarketScanRunEntity latestScan = marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc(COMPLETED_STATUS)
                .orElse(null);
        Long latestScanId = latestScan == null ? null : latestScan.getId();
        Integer latestScanTotalSymbols = latestScan == null ? null : latestScan.getTotalSymbols();
        Long latestCoinResultCount = latestScanId == null ? null : coinScanResultRepository.countByScanRun_Id(latestScanId);
        Boolean latestScanConsistent = latestScanTotalSymbols != null
                && latestCoinResultCount != null
                && latestCoinResultCount.equals(latestScanTotalSymbols.longValue());

        return new DbConsistencyResponse(
                latestScanId,
                latestScanTotalSymbols,
                latestCoinResultCount,
                latestScanConsistent,
                paperPositionRepository.countByStatus(PaperPositionStatus.OPEN),
                paperPositionRepository.countByStatus(PaperPositionStatus.CLOSED)
        );
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return String.join(",", Arrays.stream(profiles).sorted().toArray(String[]::new));
    }
}
