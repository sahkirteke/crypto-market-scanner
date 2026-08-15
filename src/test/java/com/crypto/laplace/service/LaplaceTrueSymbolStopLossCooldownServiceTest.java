package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LaplaceTrueSymbolStopLossCooldownServiceTest {
    private static final Instant EXIT = Instant.parse("2026-08-15T08:00:00Z");

    @Test void oneSecondBeforeFourHoursIsBlocked() {
        assertThat(service(EXIT.plusSeconds(4 * 3600 - 1), stopLoss("BTCUSDT")).active("BTCUSDT")).isPresent();
    }

    @Test void exactFourHourBoundaryIsAllowed() {
        assertThat(service(EXIT.plusSeconds(4 * 3600), stopLoss("BTCUSDT")).active("BTCUSDT")).isEmpty();
    }

    @Test void anotherSymbolContinuesAndNonStopLossExitDoesNotStartCooldown() {
        LaplacePaperPositionRepository repository=mock(LaplacePaperPositionRepository.class);
        when(repository.findFirstByStrategyAndSymbolAndExitReasonAndExitTimeIsNotNullOrderByExitTimeDesc(any(),eq("BTCUSDT"),eq("STOP_LOSS"))).thenReturn(Optional.of(stopLoss("BTCUSDT")));
        var service=new LaplaceTrueSymbolStopLossCooldownService(repository,new LaplaceStrategyProperties(),Clock.fixed(EXIT.plusSeconds(60),ZoneOffset.UTC));
        assertThat(service.active("BTCUSDT")).isPresent();
        assertThat(service.active("ETHUSDT")).isEmpty();
        verify(repository).findFirstByStrategyAndSymbolAndExitReasonAndExitTimeIsNotNullOrderByExitTimeDesc(any(),eq("ETHUSDT"),eq("STOP_LOSS"));
    }

    @Test void restartRebuildsActiveCooldownFromPersistedTrueStopLoss() {
        var persisted=stopLoss("BTCUSDT");
        var active=service(EXIT.plusSeconds(120),persisted).active("BTCUSDT");
        assertThat(active).isPresent();
        assertThat(active.orElseThrow().positionId()).isEqualTo("sl-position");
        assertThat(active.orElseThrow().stopLossExitTime()).isEqualTo(EXIT);
        assertThat(active.orElseThrow().cooldownUntil()).isEqualTo(EXIT.plusSeconds(4*3600));
    }

    private LaplaceTrueSymbolStopLossCooldownService service(Instant now,LaplacePaperPositionEntity persisted){
        LaplacePaperPositionRepository repository=mock(LaplacePaperPositionRepository.class);
        when(repository.findFirstByStrategyAndSymbolAndExitReasonAndExitTimeIsNotNullOrderByExitTimeDesc(any(),any(),eq("STOP_LOSS"))).thenReturn(Optional.ofNullable(persisted));
        return new LaplaceTrueSymbolStopLossCooldownService(repository,new LaplaceStrategyProperties(),Clock.fixed(now,ZoneOffset.UTC));
    }
    private LaplacePaperPositionEntity stopLoss(String symbol){return LaplacePaperPositionEntity.builder().id("sl-position").symbol(symbol).exitReason("STOP_LOSS").exitTime(EXIT).build();}
}
