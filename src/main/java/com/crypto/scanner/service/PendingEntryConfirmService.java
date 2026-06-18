package com.crypto.scanner.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ScanType;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.domain.model.EntrySignal;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.service.PaperPositionService;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingEntryConfirmService {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String REJECT_OPEN_POSITION = "SYMBOL_ALREADY_HAS_OPEN_POSITION";

    private final EntrySignalService entrySignalService;
    private final PaperPositionService paperPositionService;
    private final ScannerProperties scannerProperties;
    private final PaperPositionRepository paperPositionRepository;
    private final Map<String, PendingConfirmCandidate> pending = new ConcurrentHashMap<>();

    public void cacheCandidatesForConfirm(ScanType scanType, Long scanRunId) {
        if (scanRunId == null || !paperAutoOpenEnabled()) {
            return;
        }
        Instant createdAt = Instant.now();
        Instant expectedConfirmAt = expectedConfirmAt(createdAt);
        List<EntrySignal> signals = entrySignalService.generateSignalsFromScanRun(scanRunId);
        for (EntrySignal signal : signals == null ? List.<EntrySignal>of() : signals) {
            boolean entryWouldBeOpened = signal != null && isEnterAction(signal.getAction());
            if (!entryWouldBeOpened) {
                logNormalScanAt55(signal, scanType, false, false, expectedConfirmAt);
                continue;
            }
            PendingConfirmCandidate candidate = new PendingConfirmCandidate(
                    key(scanType, signal.getSymbol()), scanRunId, signal.getSymbol(), scanType,
                    signal.getSide(), signal.getSourceClassification(), createdAt, expectedConfirmAt);
            pending.put(candidate.key(), candidate);
            logNormalScanAt55(signal, scanType, true, true, expectedConfirmAt);
        }
    }

    @Scheduled(cron = "30 0 * * * *", zone = "${scanner.scheduler.zone}")
    public void confirmDueCandidates() {
        confirmEntryCandidates(Instant.now());
    }

    public PaperPositionService.PaperOpenSummary confirmEntryCandidates(Instant now) {
        purgeExpired(now);
        List<PendingConfirmCandidate> due = pending.values().stream()
                .filter(candidate -> !candidate.expectedConfirmAt().isAfter(now))
                .sorted(confirmPriority())
                .toList();
        List<EntrySignal> openable = new ArrayList<>();
        List<String> reservedSymbols = new ArrayList<>();
        int candidateCount = due.size();
        for (PendingConfirmCandidate candidate : due) {
            pending.remove(candidate.key());
            if (hasOpenPosition(candidate.symbol()) || reservedSymbols.contains(candidate.symbol())) {
                logConfirm(candidate, false, false, REJECT_OPEN_POSITION, null);
                continue;
            }
            EntrySignal signal = recalculateSignal(candidate);
            boolean confirmPassed = signal != null && isEnterAction(signal.getAction());
            if (confirmPassed) {
                openable.add(signal);
                reservedSymbols.add(candidate.symbol());
            }
            logConfirm(candidate, confirmPassed, false, signal == null ? "SIGNAL_NOT_FOUND" : signal.getBlockReason(), signal);
        }
        PaperPositionService.PaperOpenSummary summary = openable.isEmpty()
                ? new PaperPositionService.PaperOpenSummary(candidateCount, 0, 0, 0, 0, 0, 0, openPositionsAfter(), List.of())
                : paperPositionService.openPositions(openable);
        for (EntrySignal signal : openable) {
            log.info("ENTRY_CONFIRM_OPEN_RESULT confirmMode=CACHED_SYMBOL_CONFIRM symbol={} scanType={} confirmPassed=true entryOpened=true rejectReason= recalculatedEntryScore={} recalculatedLongScore={} recalculatedShortScore={}",
                    signal.getSymbol(), signal.getSourceScanType(), signal.getScore(), signal.getLongScore(), signal.getShortScore());
        }
        return summary;
    }

    private EntrySignal recalculateSignal(PendingConfirmCandidate candidate) {
        List<EntrySignal> signals = entrySignalService.generateSignalsFromScanRun(candidate.scanRunId());
        return (signals == null ? List.<EntrySignal>of() : signals).stream()
                .filter(signal -> signal != null && candidate.symbol().equals(signal.getSymbol()))
                .findFirst()
                .orElse(null);
    }

    private void logNormalScanAt55(EntrySignal signal, ScanType scanType, boolean entryWouldBeOpened, boolean cachedForConfirm, Instant expectedConfirmAt) {
        log.info("ENTRY_CONFIRM_CANDIDATE_SCAN scanMode=NORMAL_SCAN_AT_55 symbol={} scanType={} signalSide={} sourceClassification={} entryWouldBeOpened={} cachedForConfirm={} expectedConfirmAt={}",
                signal == null ? null : signal.getSymbol(),
                scanType,
                signal == null ? null : signal.getSide(),
                signal == null ? null : signal.getSourceClassification(),
                entryWouldBeOpened,
                cachedForConfirm,
                IstanbulTimeUtil.format(expectedConfirmAt));
    }

    private void logConfirm(PendingConfirmCandidate candidate, boolean confirmPassed, boolean entryOpened, String rejectReason, EntrySignal signal) {
        log.info("ENTRY_CONFIRM_RESULT confirmMode=CACHED_SYMBOL_CONFIRM symbol={} scanType={} confirmPassed={} entryOpened={} rejectReason={} recalculatedEntryScore={} recalculatedLongScore={} recalculatedShortScore={}",
                candidate.symbol(), candidate.scanType(), confirmPassed, entryOpened, rejectReason == null ? "" : rejectReason,
                signal == null ? null : signal.getScore(), signal == null ? null : signal.getLongScore(), signal == null ? null : signal.getShortScore());
    }

    private Instant expectedConfirmAt(Instant createdAt) {
        return createdAt.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS).plusSeconds(30);
    }

    private void purgeExpired(Instant now) {
        pending.values().removeIf(candidate -> candidate.createdAt().plus(TTL).isBefore(now));
    }

    private Comparator<PendingConfirmCandidate> confirmPriority() {
        return Comparator.comparing((PendingConfirmCandidate candidate) -> candidate.scanType() == ScanType.FOUR_HOUR ? 0 : 1)
                .thenComparing(PendingConfirmCandidate::symbol);
    }

    private boolean hasOpenPosition(String symbol) {
        return paperPositionRepository.existsBySymbolAndStatusIn(symbol, List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED));
    }

    private int openPositionsAfter() {
        return paperPositionRepository.findByStatusInOrderByOpenedAtDesc(List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED)).size();
    }

    private boolean paperAutoOpenEnabled() {
        ScannerProperties.PaperAuto paperAuto = scannerProperties.getPaperAuto();
        return paperAuto != null && Boolean.TRUE.equals(paperAuto.getEnabled()) && Boolean.TRUE.equals(paperAuto.getOpenAfterScan());
    }

    private boolean isEnterAction(EntryAction action) {
        return action == EntryAction.ENTER_LONG || action == EntryAction.ENTER_SHORT;
    }

    private String key(ScanType scanType, String symbol) {
        return "confirm-candidate:" + scanType + ":" + symbol;
    }

    public record PendingConfirmCandidate(
            String key,
            Long scanRunId,
            String symbol,
            ScanType scanType,
            PositionSide signalSide,
            CoinClassification sourceClassification,
            Instant createdAt,
            Instant expectedConfirmAt) {}
}
