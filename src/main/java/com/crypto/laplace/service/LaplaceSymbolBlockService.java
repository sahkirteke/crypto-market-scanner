package com.crypto.laplace.service;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.laplace.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceSymbolBlockService {
 private final LaplaceSymbolBlockRepository blocks;
 public boolean isBlocked(String symbol) { return blocks.existsActive(symbol, Instant.now()); }
 public void blockForStop(String symbol, String positionId, Instant stoppedAt) {
  Instant until=stoppedAt.atZone(IstanbulTimeUtil.ISTANBUL_ZONE).toLocalDate().plusDays(1).atStartOfDay(IstanbulTimeUtil.ISTANBUL_ZONE).toInstant();
  blocks.save(LaplaceSymbolBlockEntity.builder().id(UUID.randomUUID().toString()).symbol(symbol).reason("STOP_LOSS").blockedAt(stoppedAt).blockedUntil(until).sourcePositionId(positionId).createdAt(Instant.now()).build());
 }
}
