package com.crypto.laplace.scheduler;

import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.service.LaplaceProfitLockService;
import com.crypto.laplace.service.LaplaceRuntimeService;
import com.crypto.laplace.service.LaplaceMarketDataGate;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class LaplaceProfitLockScheduler {
    private final LaplaceRuntimeService runtime;
    private final LaplaceProfitLockService profitLock;
    private final LaplaceMarketDataGate marketDataGate;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${trading.laplace.stop-loss-cron}", zone = "${trading.laplace.zone}")
    public void evaluate() {
        if (!marketDataGate.enabledTrue() || !running.compareAndSet(false, true)) return;
        try {
            if (runtime.current().getRuntimeState() == LaplaceRuntimeState.ACTIVE) profitLock.evaluate();
            else if (runtime.current().getRuntimeState() == LaplaceRuntimeState.LIQUIDATING) profitLock.retryLiquidation();
        } finally {
            running.set(false);
        }
    }
}
