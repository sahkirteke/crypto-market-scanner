package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceRuntimeState;
import com.crypto.laplace.persistence.LaplaceTradingSessionEntity;
import com.crypto.laplace.persistence.LaplaceTradingSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplaceRuntimeServiceTest {
    private final Instant now = Instant.parse("2026-08-14T12:00:00Z");
    private LaplaceTradingSessionRepository repository;
    private AtomicReference<LaplaceTradingSessionEntity> stored;
    private LaplaceRuntimeService service;

    @BeforeEach
    void setUp() {
        repository = mock(LaplaceTradingSessionRepository.class);
        stored = new AtomicReference<>();
        when(repository.findTopByOrderBySessionStartTimeDesc()).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.findCurrentForUpdate()).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return call.getArgument(0); });
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return call.getArgument(0); });
        service = new LaplaceRuntimeService(repository, new LaplaceStrategyProperties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void firstSessionStartsWithRequiredCapitalMarginLeverageAndNotional() {
        service.run(null);
        var status = service.status();
        assertThat(status.runtimeState()).isEqualTo(LaplaceRuntimeState.ACTIVE);
        assertThat(status.sessionStartCapital()).isEqualByComparingTo("350");
        assertThat(status.marginPerPosition()).isEqualByComparingTo("5");
        assertThat(status.leverage()).isEqualTo(15);
        assertThat(status.positionNotional()).isEqualByComparingTo("75");
        assertThat(status.profitTargetUsdt()).isEqualByComparingTo("17.5");
        assertThat(status.minimumLockedProfitUsdt()).isEqualByComparingTo("16.45");
    }

    @Test
    void exactFivePercentLockedProfitCompoundsCapitalMarginNotionalAndNextTarget() {
        service.run(null);
        assertThat(service.beginLiquidation(bd("0"), bd("17.6"), bd("0.1"), bd("17.5"))).isTrue();
        service.recordLiquidationResult(bd("17.50"));
        assertThat(stored.get().getSessionStartCapital()).isEqualByComparingTo("350");
        assertThat(stored.get().getNextSessionCapital()).isEqualByComparingTo("367.50");
        assertThat(stored.get().getNextMarginPerPosition()).isEqualByComparingTo("5.25");
        assertThat(stored.get().getNextPositionNotional()).isEqualByComparingTo("78.75");
        service.startCooldown(now);
        assertThat(stored.get().getCooldownUntil()).isEqualTo(now.plusSeconds(21600));
        stored.get().setCooldownUntil(now);
        assertThat(service.beginInitializationIfDue()).isTrue();
        service.activateNextSession();
        assertThat(service.current().getSessionStartCapital()).isEqualByComparingTo("367.50");
        assertThat(service.status().profitTargetUsdt()).isEqualByComparingTo("18.375");
    }

    @Test
    void targetAlwaysUsesFivePercentOfCurrentSessionOpeningCapital() {
        var current = LaplaceTradingSessionEntity.builder()
                .sessionStartCapital(bd("367.50")).profitTargetPct(bd("99")).build();
        assertThat(service.target(current)).isEqualByComparingTo("18.375");
    }

    @Test
    void actualNonFivePercentProfitControlsGrowthWithoutEarlyRounding() {
        service.run(null);
        service.beginLiquidation(BigDecimal.ZERO, bd("17"), BigDecimal.ZERO, bd("17"));
        service.recordLiquidationResult(bd("16.975"));
        assertThat(stored.get().getNextSessionCapital()).isEqualByComparingTo("366.975");
        assertThat(stored.get().getCapitalGrowthFactor()).isEqualByComparingTo("1.0485");
        assertThat(stored.get().getNextMarginPerPosition()).isEqualByComparingTo("5.2425");
        assertThat(stored.get().getNextPositionNotional()).isEqualByComparingTo("78.6375");
    }

    @Test
    void restartPreservesCompoundedSizingAndRemainingCooldown() {
        service.run(null);
        service.beginLiquidation(BigDecimal.ZERO, bd("18"), bd("0.5"), bd("17.5"));
        service.recordLiquidationResult(bd("17.5"));
        service.startCooldown(now);

        LaplaceRuntimeService restarted = new LaplaceRuntimeService(repository,
                new LaplaceStrategyProperties(), Clock.fixed(now.plusSeconds(3600), ZoneOffset.UTC));
        restarted.run(null);

        assertThat(restarted.status().runtimeState()).isEqualTo(LaplaceRuntimeState.COOLDOWN);
        assertThat(restarted.status().cooldownUntil()).isEqualTo(now.plusSeconds(21600));
        assertThat(stored.get().getNextSessionCapital()).isEqualByComparingTo("367.5");
        assertThat(stored.get().getNextMarginPerPosition()).isEqualByComparingTo("5.25");
        assertThat(restarted.beginInitializationIfDue()).isFalse();
    }

    @Test
    void oneSecondBeforeCooldownUntilDoesNotInitializeButExactBoundaryDoes() {
        service.run(null);
        service.beginLiquidation(BigDecimal.ZERO, bd("18"), bd("0.5"), bd("17.5"));
        service.recordLiquidationResult(bd("17.5"));
        service.startCooldown(now);
        var before = new LaplaceRuntimeService(repository, new LaplaceStrategyProperties(),
                Clock.fixed(now.plusSeconds(21599), ZoneOffset.UTC));
        assertThat(before.beginInitializationIfDue()).isFalse();
        var exact = new LaplaceRuntimeService(repository, new LaplaceStrategyProperties(),
                Clock.fixed(now.plusSeconds(21600), ZoneOffset.UTC));
        assertThat(exact.beginInitializationIfDue()).isTrue();
        assertThat(stored.get().getNextSessionCapital()).isEqualByComparingTo("367.5");
        assertThat(stored.get().getNextMarginPerPosition()).isEqualByComparingTo("5.25");
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
