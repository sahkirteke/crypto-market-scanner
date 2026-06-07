package com.crypto.scanner.runner;

import com.crypto.domain.model.EntryCandidate;
import com.crypto.scanner.service.EntryCandidateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-entry-candidate")
@RequiredArgsConstructor
public class EntryCandidateManualRunner implements CommandLineRunner {
    private final EntryCandidateService entryCandidateService;

    @Override
    public void run(String... args) {
        log.info("MANUAL_ENTRY_CANDIDATE_CHECK started");
        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromLatestScan();
        log.info("MANUAL_ENTRY_CANDIDATE_CHECK total={}", candidates.size());
        candidates.forEach(candidate -> log.info(
                "MANUAL_ENTRY_CANDIDATE_CHECK symbol={} side={} score={} classification={} riskLevel={} reason={}",
                candidate.getSymbol(),
                candidate.getSide(),
                candidate.getScore(),
                candidate.getSourceClassification(),
                candidate.getRiskLevel(),
                candidate.getCandidateReason()
        ));
        log.info("MANUAL_ENTRY_CANDIDATE_CHECK completed");
    }
}
