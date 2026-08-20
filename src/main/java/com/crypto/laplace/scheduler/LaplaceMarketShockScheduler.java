package com.crypto.laplace.scheduler;

import com.crypto.laplace.service.LaplaceMarketDataGate;
import com.crypto.laplace.service.LaplaceMarketShockService;
import com.crypto.laplace.service.LaplaceRuntimeService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LaplaceMarketShockScheduler {
    private final LaplaceMarketDataGate gate;
    private final LaplaceRuntimeService runtime;
    private final LaplaceMarketShockService shock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${trading.laplace.shock-cron}", zone = "${trading.laplace.zone}")
    public void evaluate() {
        if (!gate.allowsMarketData() || !runtime.isActive() || !running.compareAndSet(false, true)) return;
        try { shock.evaluate(); }
        catch (RuntimeException failure) { log.error("LAPLACE_MARKET_SHOCK_EVALUATION_FAILED", failure); }
        finally { running.set(false); }
    }

    public void clearRuntimeState() { shock.clearRuntimeState(); running.set(false); }
}
