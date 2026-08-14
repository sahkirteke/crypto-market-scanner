package com.crypto.laplace.scheduler;

import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.service.LaplaceInvertedFalseProfitLockService;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class LaplaceInvertedFalseProfitLockScheduler {
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final LaplaceInvertedFalseProfitLockService profitLock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${trading.laplace.stop-loss-cron}", zone = "${trading.laplace.zone}")
    public void evaluate() {
        if (!running.compareAndSet(false, true)) return;
        try {
            if (runtime.current().getRuntimeState() == LaplaceRuntimeState.ACTIVE) profitLock.evaluate();
            else if (runtime.current().getRuntimeState() == LaplaceRuntimeState.LIQUIDATING) profitLock.retryLiquidation();
        } finally {
            running.set(false);
        }
    }
}
