package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Entry-time snapshot only; failures are excluded from the valid-coin denominator. */
@Service @RequiredArgsConstructor
public class LaplaceBreadthService {
 private final BinanceFuturesClient client; private final StartupMarketUniverseService universe;
 public double positiveBreadth30Pct(Instant cutoff) {
  int valid=0,positive=0;
  for(String symbol:universe.symbols()) try {
   Kline candle=client.getKlines(symbol,"30m",3).stream().filter(c->c!=null&&c.getOpen()!=null&&c.getClose()!=null&&c.getCloseTime()!=null)
    .filter(c->!c.getCloseTime().isAfter(cutoff)&&!Boolean.FALSE.equals(c.getClosed())).max(java.util.Comparator.comparing(Kline::getCloseTime)).orElse(null);
   if(candle==null||candle.getOpen().signum()<=0)continue;valid++;if(candle.getClose().compareTo(candle.getOpen())>0)positive++;
  } catch(RuntimeException ignored) { /* invalid coins do not enter either count */ }
  return valid==0?Double.NaN:positive*100d/valid;
 }
}
