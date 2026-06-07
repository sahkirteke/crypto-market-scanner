package com.crypto.api.controller;

import com.crypto.api.dto.EntryCandidateResponse;
import com.crypto.api.dto.EntrySignalResponse;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.scanner.service.EntryCandidateService;
import com.crypto.scanner.service.EntrySignalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
public class EntryCandidateController {
    private final EntryCandidateService entryCandidateService;
    private final EntrySignalService entrySignalService;

    @GetMapping("/latest/candidates")
    public List<EntryCandidateResponse> getLatestCandidates() {
        return entryCandidateService.selectCandidatesFromLatestScan().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/latest/signals")
    public List<EntrySignalResponse> getLatestSignals() {
        return entrySignalService.generateSignalsFromLatestScan().stream()
                .map(this::toResponse)
                .toList();
    }

    private EntryCandidateResponse toResponse(EntryCandidate candidate) {
        return new EntryCandidateResponse(
                candidate.getSymbol(),
                candidate.getSide(),
                candidate.getScore(),
                candidate.getLongScore(),
                candidate.getShortScore(),
                candidate.getSourceClassification(),
                candidate.getDirectionBias(),
                candidate.getRiskLevel(),
                candidate.getLastPrice(),
                candidate.getPriceChange24hPct(),
                candidate.getQuoteVolume24h(),
                candidate.getSpreadPct(),
                candidate.getFundingRate(),
                candidate.getOpenInterest(),
                candidate.getMarketBreadthPct(),
                candidate.getReasons(),
                candidate.getWarnings(),
                candidate.getCandidateReason(),
                candidate.getCreatedAt()
        );
    }

    private EntrySignalResponse toResponse(EntrySignal signal) {
        return new EntrySignalResponse(
                signal.getSymbol(),
                signal.getSide(),
                signal.getAction(),
                signal.getScore(),
                signal.getLongScore(),
                signal.getShortScore(),
                signal.getSourceClassification(),
                signal.getDirectionBias(),
                signal.getRiskLevel(),
                signal.getEntryPrice(),
                signal.getLastPrice(),
                signal.getSpreadPct(),
                signal.getPriceChange24hPct(),
                signal.getQuoteVolume24h(),
                signal.getFundingRate(),
                signal.getOpenInterest(),
                signal.getMarketBreadthPct(),
                signal.getReasons(),
                signal.getWarnings(),
                signal.getSignalReason(),
                signal.getBlockReason(),
                signal.getSignalTime(),
                signal.getEntryTrigger(),
                signal.getClose1h(),
                signal.getPreviousClose1h(),
                signal.getPrevious1hHigh(),
                signal.getPrevious1hLow(),
                signal.getEma20_1h(),
                signal.getRsi14_1h(),
                signal.getPreviousRsi14_1h(),
                signal.getMacdHist_1h(),
                signal.getPreviousMacdHist_1h(),
                signal.getVolumeRatio_1h()
        );
    }

}
