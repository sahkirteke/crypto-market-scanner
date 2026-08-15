package com.crypto.laplace.service;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Persistent, TRUE-only per-symbol cooldown derived from the latest stop-loss exit. */
@Service @RequiredArgsConstructor
public class LaplaceTrueSymbolStopLossCooldownService {
    private final LaplacePaperPositionRepository positions;
    private final LaplaceStrategyProperties properties;
    private final Clock clock;

    public Optional<ActiveCooldown> active(String symbol) {
        Duration duration = properties.getLaplace().getPaper().getInvertedTrue().getSymbolStopLossCooldown();
        return positions.findFirstByStrategyAndSymbolAndExitReasonAndExitTimeIsNotNullOrderByExitTimeDesc(
                        LaplacePaperExecutionService.STRATEGY, symbol, "STOP_LOSS")
                .map(position -> new ActiveCooldown(position.getId(), position.getExitTime(),
                        position.getExitTime(), position.getExitTime().plus(duration)))
                .filter(cooldown -> clock.instant().isBefore(cooldown.cooldownUntil()));
    }

    public long remainingSeconds(ActiveCooldown cooldown) {
        return Math.max(0, Duration.between(clock.instant(), cooldown.cooldownUntil()).getSeconds());
    }

    public record ActiveCooldown(String positionId, Instant stopLossExitTime,
                                 Instant cooldownStartedAt, Instant cooldownUntil) {}
}
