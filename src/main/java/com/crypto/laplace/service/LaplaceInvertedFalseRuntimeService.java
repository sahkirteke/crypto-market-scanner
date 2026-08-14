package com.crypto.laplace.service;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.persistence.LaplaceInvertedFalseSessionEntity;
import com.crypto.laplace.persistence.LaplaceInvertedFalseSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Order(2)
public class LaplaceInvertedFalseRuntimeService implements ApplicationRunner {
    private final LaplaceInvertedFalseSessionRepository sessions;
    private final LaplaceStrategyProperties properties;
    private final Clock clock;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled() || sessions.findTopByOrderBySessionStartTimeDesc().isPresent()) return;
        var config = properties.getLaplace().getPaper().getInvertedFalse();
        BigDecimal capital = config.getInitialCapitalUsdt();
        BigDecimal margin = config.getMarginPerPositionUsdt();
        sessions.save(LaplaceInvertedFalseSessionEntity.builder().sessionId(UUID.randomUUID().toString())
                .sessionStartTime(clock.instant()).sessionStartCapital(capital).marginPerPosition(margin)
                .startingNotional(margin.multiply(BigDecimal.valueOf(config.getLeverage())))
                .leverage(config.getLeverage()).profitTargetPct(new BigDecimal("5"))
                .minimumLockedProfitPct(new BigDecimal("4.7")).profitTargetUsdt(capital.multiply(new BigDecimal("0.05")))
                .minimumLockedProfitUsdt(capital.multiply(new BigDecimal("0.047")))
                .realizedSessionNetPnl(BigDecimal.ZERO).runtimeState(LaplaceRuntimeState.ACTIVE).build());
    }
    public boolean enabled() { return properties.getLaplace().getPaper().getInvertedFalse().isEnabled(); }
    public boolean isActive() { return enabled() && current().getRuntimeState() == LaplaceRuntimeState.ACTIVE; }
    public LaplaceInvertedFalseSessionEntity current() { return sessions.findTopByOrderBySessionStartTimeDesc().orElseThrow(); }
}
