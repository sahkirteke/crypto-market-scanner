package com.crypto.laplace.service;
import com.crypto.laplace.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceSymbolBlockService {
 private final LaplaceSymbolBlockRepository blocks;
 public boolean isBlocked(String symbol) { return isBlocked(symbol, Instant.now()); }
 /** Queries persistence on every call so a process restart retains the original block expiry. */
 boolean isBlocked(String symbol, Instant at) { return blocks.existsActive(symbol, at); }
 public void blockForStop(String symbol, String positionId, Instant stoppedAt) {
  Instant until=stoppedAt.plus(Duration.ofHours(24));
  blocks.save(LaplaceSymbolBlockEntity.builder().id(UUID.randomUUID().toString()).symbol(symbol).reason("STOP_LOSS").blockedAt(stoppedAt).blockedUntil(until).sourcePositionId(positionId).createdAt(Instant.now()).build());
 }
}
