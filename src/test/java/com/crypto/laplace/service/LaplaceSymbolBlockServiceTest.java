package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.laplace.persistence.LaplaceSymbolBlockEntity;
import com.crypto.laplace.persistence.LaplaceSymbolBlockRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LaplaceSymbolBlockServiceTest {
    private LaplaceSymbolBlockRepository repository;
    private LaplaceSymbolBlockService service;
    private final Instant blockedAt = Instant.parse("2026-07-20T11:35:00Z"); // 14:35 Europe/Istanbul

    @BeforeEach
    void setUp() {
        repository = mock(LaplaceSymbolBlockRepository.class);
        service = new LaplaceSymbolBlockService(repository);
    }

    @Test
    void stopBlockRemainsActiveAtFourteenThirtyFourTheNextDay() {
        when(repository.existsActive(eq("BTCUSDT"), eq(blockedAt.plusSeconds(24 * 60 * 60 - 60)))).thenReturn(true);
        assertThat(service.isBlocked("BTCUSDT", blockedAt.plusSeconds(24 * 60 * 60 - 60))).isTrue();
    }

    @Test
    void stopBlockEndsExactlyTwentyFourHoursLater() {
        when(repository.existsActive(eq("BTCUSDT"), eq(blockedAt.plusSeconds(24 * 60 * 60)))).thenReturn(false);
        assertThat(service.isBlocked("BTCUSDT", blockedAt.plusSeconds(24 * 60 * 60))).isFalse();
    }

    @Test
    void midnightDoesNotEndTheRollingBlock() {
        Instant istanbulMidnight = Instant.parse("2026-07-20T21:00:00Z");
        when(repository.existsActive(eq("BTCUSDT"), eq(istanbulMidnight))).thenReturn(true);
        assertThat(service.isBlocked("BTCUSDT", istanbulMidnight)).isTrue();
    }

    @Test
    void persistedBlockKeepsTheSameExpiryAfterRestart() {
        service.blockForStop("BTCUSDT", "position", blockedAt);
        ArgumentCaptor<LaplaceSymbolBlockEntity> saved = ArgumentCaptor.forClass(LaplaceSymbolBlockEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getBlockedUntil()).isEqualTo(blockedAt.plusSeconds(24 * 60 * 60));

        LaplaceSymbolBlockService restarted = new LaplaceSymbolBlockService(repository);
        Instant beforeExpiry = saved.getValue().getBlockedUntil().minusSeconds(1);
        when(repository.existsActive("BTCUSDT", beforeExpiry)).thenReturn(true);
        assertThat(restarted.isBlocked("BTCUSDT", beforeExpiry)).isTrue();
        assertThat(saved.getValue().getBlockedAt().atZone(ZoneOffset.ofHours(3)).getHour()).isEqualTo(14);
    }
}
