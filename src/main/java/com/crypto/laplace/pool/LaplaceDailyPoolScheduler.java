package com.crypto.laplace.pool;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs once per New York calendar day, after the 09:30-10:00 closed 30m bar. */
@Slf4j @Component @RequiredArgsConstructor
public class LaplaceDailyPoolScheduler {
 private final BinanceFuturesClient client; private final LaplaceCoinPoolService pool;
 @Scheduled(cron="${trading.laplace.daily-pool-cron:5 0 10 * * *}", zone="${trading.laplace.daily-pool-zone:America/New_York}")
 public void refresh() {
  try { List<SymbolInfo> exchange=client.getExchangeInfo(); Set<String> eligible=new HashSet<>(); for(SymbolInfo s:exchange) if(s!=null&&"USDT".equals(s.getQuoteAsset())&&"PERPETUAL".equals(s.getContractType())&&"TRADING".equals(s.getStatus()))eligible.add(s.getSymbol());
   Map<String,BigDecimal> volumes=new HashMap<>(); for(Ticker24h t:client.getAll24hTickers()) if(t!=null&&eligible.contains(t.getSymbol())&&t.getQuoteVolume()!=null)volumes.put(t.getSymbol(),t.getQuoteVolume());
   pool.refresh(eligible,volumes,Instant.now());
  } catch(RuntimeException e) { log.error("LAPLACE_POOL_REFRESH_FAILED existingSnapshotPreserved=true",e); }
 }
}
