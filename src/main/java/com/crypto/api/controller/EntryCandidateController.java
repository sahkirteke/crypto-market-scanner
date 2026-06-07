package com.crypto.api.controller;

import com.crypto.api.dto.EntryCandidateResponse;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.scanner.service.EntryCandidateService;
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

    @GetMapping("/latest/candidates")
    public List<EntryCandidateResponse> getLatestCandidates() {
        return entryCandidateService.selectCandidatesFromLatestScan().stream()
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
}
