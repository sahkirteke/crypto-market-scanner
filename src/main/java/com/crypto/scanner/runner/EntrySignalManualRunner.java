package com.crypto.scanner.runner;

import com.crypto.domain.model.EntrySignal;
import com.crypto.scanner.service.EntrySignalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-entry-signal")
@RequiredArgsConstructor
public class EntrySignalManualRunner implements CommandLineRunner {
    private final EntrySignalService entrySignalService;

    @Override
    public void run(String... args) {
        log.info("MANUAL_ENTRY_SIGNAL_CHECK started");
        List<EntrySignal> signals = entrySignalService.generateSignalsFromLatestScan();
        log.info("MANUAL_ENTRY_SIGNAL_CHECK total={}", signals.size());
        signals.forEach(signal -> log.info(
                "MANUAL_ENTRY_SIGNAL_CHECK symbol={} side={} action={} score={} reason={} blockReason={}",
                signal.getSymbol(),
                signal.getSide(),
                signal.getAction(),
                signal.getScore(),
                signal.getSignalReason(),
                signal.getBlockReason()
        ));
        log.info("MANUAL_ENTRY_SIGNAL_CHECK completed");
    }
}
