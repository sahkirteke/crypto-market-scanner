package com.crypto.laplace.service;

import com.crypto.laplace.api.LaplaceRuntimeStatusResponse;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.persistence.LaplaceTradingSessionEntity;
import com.crypto.laplace.persistence.LaplaceTradingSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Order(1)
public class LaplaceRuntimeService implements ApplicationRunner {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 12;
    private static final Duration COOLDOWN = Duration.ofHours(6);
    private final LaplaceTradingSessionRepository sessions;
    private final LaplaceStrategyProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sessions.findTopByOrderBySessionStartTimeDesc().isEmpty()) {
            sessions.save(initialSession());
        }
    }

    public boolean isActive() {
        return current().getRuntimeState() == LaplaceRuntimeState.ACTIVE;
    }

    public boolean allowsMarketData() {
        LaplaceRuntimeState state = current().getRuntimeState();
        return state == LaplaceRuntimeState.ACTIVE || state == LaplaceRuntimeState.INITIALIZING;
    }

    public LaplaceTradingSessionEntity current() {
        return sessions.findTopByOrderBySessionStartTimeDesc().orElseThrow(() -> new IllegalStateException("LAPLACE_SESSION_UNAVAILABLE"));
    }

    @Transactional
    public boolean beginLiquidation(BigDecimal realized, BigDecimal openPnl, BigDecimal exitFees,
                                    BigDecimal estimatedAfterClose) {
        LaplaceTradingSessionEntity session = locked();
        if (session.getRuntimeState() != LaplaceRuntimeState.ACTIVE) return false;
        session.setRuntimeState(LaplaceRuntimeState.LIQUIDATING);
        session.setRealizedSessionNetPnl(realized);
        session.setOpenPositionPnl(openPnl);
        session.setEstimatedExitFees(exitFees);
        session.setEstimatedNetProfitAfterClose(estimatedAfterClose);
        session.setProfitLockTriggeredAt(clock.instant());
        sessions.saveAndFlush(session);
        return true;
    }

    @Transactional
    public void recordLiquidationResult(BigDecimal actualProfit) {
        LaplaceTradingSessionEntity session = locked();
        if (session.getRuntimeState() != LaplaceRuntimeState.LIQUIDATING) return;
        BigDecimal nextCapital = session.getSessionStartCapital().add(actualProfit);
        BigDecimal factor = nextCapital.divide(session.getSessionStartCapital(), SCALE, RoundingMode.HALF_UP);
        BigDecimal nextMargin = session.getMarginPerPosition().multiply(factor);
        session.setActualLockedSessionProfit(actualProfit);
        session.setCapitalGrowthFactor(factor);
        session.setNextSessionCapital(nextCapital);
        session.setNextMarginPerPosition(nextMargin);
        session.setNextPositionNotional(nextMargin.multiply(BigDecimal.valueOf(session.getLeverage())));
        sessions.saveAndFlush(session);
    }

    @Transactional
    public void startCooldown(Instant lastExitTime) {
        LaplaceTradingSessionEntity session = locked();
        if (session.getRuntimeState() != LaplaceRuntimeState.LIQUIDATING
                || session.getNextSessionCapital() == null) throw new IllegalStateException("LIQUIDATION_NOT_COMPLETE");
        session.setCooldownStartedAt(lastExitTime);
        session.setCooldownUntil(lastExitTime.plus(COOLDOWN));
        session.setRuntimeState(LaplaceRuntimeState.COOLDOWN);
        sessions.saveAndFlush(session);
    }

    @Transactional
    public boolean beginInitializationIfDue() {
        LaplaceTradingSessionEntity session = locked();
        if (session.getRuntimeState() == LaplaceRuntimeState.INITIALIZING) return true;
        if (session.getRuntimeState() != LaplaceRuntimeState.COOLDOWN
                || clock.instant().isBefore(session.getCooldownUntil())) return false;
        session.setRuntimeState(LaplaceRuntimeState.INITIALIZING);
        sessions.saveAndFlush(session);
        return true;
    }

    @Transactional
    public LaplaceTradingSessionEntity activateNextSession() {
        LaplaceTradingSessionEntity previous = locked();
        if (previous.getRuntimeState() != LaplaceRuntimeState.INITIALIZING) throw new IllegalStateException("INVALID_RUNTIME_TRANSITION");
        LaplaceTradingSessionEntity next = LaplaceTradingSessionEntity.builder()
                .sessionId(UUID.randomUUID().toString()).sessionStartTime(clock.instant())
                .sessionStartCapital(previous.getNextSessionCapital()).marginPerPosition(previous.getNextMarginPerPosition())
                .startingNotional(previous.getNextPositionNotional())
                .leverage(previous.getLeverage()).profitTargetPct(previous.getProfitTargetPct())
                .minimumLockedProfitPct(previous.getMinimumLockedProfitPct())
                .profitTargetUsdt(pct(previous.getNextSessionCapital(), previous.getProfitTargetPct()))
                .minimumLockedProfitUsdt(pct(previous.getNextSessionCapital(), previous.getMinimumLockedProfitPct()))
                .realizedSessionNetPnl(BigDecimal.ZERO).runtimeState(LaplaceRuntimeState.ACTIVE).build();
        return sessions.saveAndFlush(next);
    }

    public BigDecimal target(LaplaceTradingSessionEntity session) {
        return pct(session.getSessionStartCapital(), session.getProfitTargetPct());
    }

    public BigDecimal minimumLocked(LaplaceTradingSessionEntity session) {
        return pct(session.getSessionStartCapital(), session.getMinimumLockedProfitPct());
    }

    public LaplaceRuntimeStatusResponse status() {
        LaplaceTradingSessionEntity session = current();
        return new LaplaceRuntimeStatusResponse(session.getRuntimeState(), session.getSessionId(),
                session.getSessionStartCapital(), session.getMarginPerPosition(), session.getLeverage(),
                session.getMarginPerPosition().multiply(BigDecimal.valueOf(session.getLeverage())),
                session.getProfitTargetPct(), target(session), session.getMinimumLockedProfitPct(),
                minimumLocked(session), session.getCooldownUntil());
    }

    private LaplaceTradingSessionEntity initialSession() {
        var config = properties.getLaplace();
        return LaplaceTradingSessionEntity.builder().sessionId(UUID.randomUUID().toString())
                .sessionStartTime(clock.instant()).sessionStartCapital(config.getInitialCapitalUsdt())
                .marginPerPosition(config.getMarginPerPositionUsdt())
                .startingNotional(config.getMarginPerPositionUsdt().multiply(BigDecimal.valueOf(config.getLeverage())))
                .leverage(config.getLeverage())
                .profitTargetPct(config.getProfitTargetPct()).minimumLockedProfitPct(config.getMinimumLockedProfitPct())
                .profitTargetUsdt(pct(config.getInitialCapitalUsdt(), config.getProfitTargetPct()))
                .minimumLockedProfitUsdt(pct(config.getInitialCapitalUsdt(), config.getMinimumLockedProfitPct()))
                .realizedSessionNetPnl(BigDecimal.ZERO).runtimeState(LaplaceRuntimeState.ACTIVE).build();
    }

    private LaplaceTradingSessionEntity locked() {
        return sessions.findCurrentForUpdate().orElseThrow(() -> new IllegalStateException("LAPLACE_SESSION_UNAVAILABLE"));
    }

    private BigDecimal pct(BigDecimal amount, BigDecimal pct) {
        return amount.multiply(pct).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }
}
