package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ThirtyMinuteKlineService {
 public static final String INTERVAL="30m"; private static final Duration STEP=Duration.ofMinutes(30);
 private final BinanceFuturesClient client; private final LaplaceStrategyProperties properties;
 public List<Kline> loadClosed(String symbol) {
  Instant now=Instant.now(); List<Kline> raw=client.getKlines(symbol, INTERVAL, properties.getLaplace().getKlineLimit());
  if(raw==null) throw new IllegalStateException("Null kline response");
  Map<Instant,Kline> unique=new TreeMap<>();
  for(Kline k:raw) if(k!=null && k.getCloseTime()!=null && !k.getCloseTime().isAfter(now) && !Boolean.FALSE.equals(k.getClosed())) {
   if(unique.putIfAbsent(k.getOpenTime(),k)!=null) throw new IllegalStateException("Duplicate candle");
  }
  List<Kline> result=List.copyOf(unique.values());
  if(result.size()<16) throw new IllegalStateException("Insufficient closed candles");
  for(int i=1;i<result.size();i++) if(!STEP.equals(Duration.between(result.get(i-1).getOpenTime(),result.get(i).getOpenTime()))) throw new IllegalStateException("Data gap");
  return result;
 }
}
