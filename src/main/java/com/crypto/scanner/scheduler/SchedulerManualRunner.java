package com.crypto.scanner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-scheduler")
@RequiredArgsConstructor
public class SchedulerManualRunner implements CommandLineRunner {
    private final MarketScanScheduler marketScanScheduler;

    @Override
    public void run(String... args) {
        log.info("MANUAL_SCHEDULER_CHECK started");
        marketScanScheduler.triggerFourHourScanManually();
        log.info("MANUAL_SCHEDULER_CHECK completed");
    }
}
