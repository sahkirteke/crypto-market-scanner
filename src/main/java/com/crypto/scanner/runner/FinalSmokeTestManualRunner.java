package com.crypto.scanner.runner;

import com.crypto.analysis.dto.StrategyAnalysisResponse;
import com.crypto.analysis.service.ResultAnalyzerService;
import com.crypto.common.enums.EntryAction;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.paper.service.PaperPositionQueryService;
import com.crypto.scanner.service.EntryCandidateService;
import com.crypto.scanner.service.EntrySignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("manual-final-smoke")
@RequiredArgsConstructor
@Slf4j
public class FinalSmokeTestManualRunner implements CommandLineRunner {
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final MarketScanRunRepository marketScanRunRepository;
    private final EntryCandidateService entryCandidateService;
    private final EntrySignalService entrySignalService;
    private final PaperPositionQueryService paperPositionQueryService;
    private final ResultAnalyzerService resultAnalyzerService;

    @Override
    public void run(String... args) {
        log.info("FINAL_SMOKE_STARTED");

        MarketScanRunEntity latestScan = marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc(COMPLETED_STATUS)
                .orElseThrow(() -> new IllegalStateException("Completed scanner run not found"));
        log.info("FINAL_SMOKE_LATEST_SCAN_OK id={}", latestScan.getId());

        int candidateCount = entryCandidateService.selectCandidatesFromScanRun(latestScan.getId()).size();
        log.info("FINAL_SMOKE_CANDIDATES_OK count={}", candidateCount);

        var signals = entrySignalService.generateSignalsFromScanRun(latestScan.getId());
        long enterLong = signals.stream().filter(signal -> signal.getAction() == EntryAction.ENTER_LONG).count();
        long enterShort = signals.stream().filter(signal -> signal.getAction() == EntryAction.ENTER_SHORT).count();
        long noEntry = signals.stream().filter(signal -> signal.getAction() == EntryAction.NO_ENTRY).count();
        log.info(
                "FINAL_SMOKE_SIGNALS_OK count={} enterLong={} enterShort={} noEntry={}",
                signals.size(),
                enterLong,
                enterShort,
                noEntry
        );

        var openPositions = paperPositionQueryService.getOpenPositions();
        log.info("FINAL_SMOKE_OPEN_POSITIONS_OK count={}", openPositions.size());

        StrategyAnalysisResponse analysis = resultAnalyzerService.analyzeAllClosedTrades();
        log.info(
                "FINAL_SMOKE_ANALYSIS_OK totalTrades={} winRate={}",
                analysis.summary().totalTrades(),
                analysis.summary().winRatePct()
        );

        log.info("FINAL_SMOKE_COMPLETED");
    }
}
