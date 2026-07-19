package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor @Order(100)
@ConditionalOnProperty(prefix="trading.laplace", name="enabled", havingValue="true")
public class StartupMarketUniverseService implements ApplicationRunner {
 private final BinanceFuturesClient client;
 private final ScannerProperties scannerProperties;
 private final AtomicBoolean attempted = new AtomicBoolean();
 private volatile Set<String> symbols = Set.of();
 private volatile Map<String, BigDecimal> startupVolumes = Map.of();
 private final String sessionId = UUID.randomUUID().toString();
 private volatile boolean ready;
 @Override public void run(ApplicationArguments args) { initialize(); }
 public synchronized void initialize() {
  if (!attempted.compareAndSet(false, true)) return;
  rebuild();
 }
 /** Builds a complete candidate set before replacing the active universe. */
 public synchronized boolean refresh() { return rebuild(); }
 private boolean rebuild() {
  BigDecimal threshold=scannerProperties.getLiquidity().getMinQuoteVolume24h();
  log.info("MARKET_UNIVERSE_INITIALIZATION_STARTED minimumVolumeThreshold={}", threshold);
  try {
   List<SymbolInfo> exchange=client.getExchangeInfo(); List<Ticker24h> tickers=client.getAll24hTickers();
   Set<String> tradable=new HashSet<>();
   for (SymbolInfo s: exchange) if (s!=null && "USDT".equals(s.getQuoteAsset()) && "PERPETUAL".equals(s.getContractType()) && "TRADING".equals(s.getStatus())) tradable.add(s.getSymbol());
   Set<String> eligible=new TreeSet<>(); Map<String, BigDecimal> volumes=new HashMap<>();
   for (Ticker24h t: tickers) {
    if (t==null || !tradable.contains(t.getSymbol()) || t.getQuoteVolume()==null || t.getQuoteVolume().compareTo(threshold)<0) continue;
    BigDecimal high=t.getHighPrice(), low=t.getLowPrice();
    if (low==null || low.signum()<=0 || high==null || high.signum()<0) { diagnostic(t, null, false, "RANGE_24H_INVALID"); continue; }
    BigDecimal range=high.subtract(low).divide(low, 12, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    if (range.compareTo(BigDecimal.TEN)>=0) { diagnostic(t, range, false, "RANGE_24H_TOO_HIGH"); continue; }
    diagnostic(t, range, true, null); eligible.add(t.getSymbol()); volumes.put(t.getSymbol(),t.getQuoteVolume());
   }
   symbols=Set.copyOf(eligible); startupVolumes=Map.copyOf(volumes); ready=!symbols.isEmpty();
   log.info("MARKET_UNIVERSE_INITIALIZATION_COMPLETED totalSymbols={} eligibleSymbols={} minimumVolumeThreshold={}", tradable.size(), symbols.size(), threshold);
   log.debug("MARKET_UNIVERSE_SYMBOLS symbols={}", symbols);
   if (!ready) log.error("MARKET_UNIVERSE_INITIALIZATION_EMPTY schedulerDisabled=true");
   return ready;
  } catch (RuntimeException e) { log.error("MARKET_UNIVERSE_INITIALIZATION_FAILED keepingPreviousUniverse=true message={}", e.getMessage(), e); return false; }
 }
 private void diagnostic(Ticker24h t, BigDecimal range, boolean eligible, String rejection) { log.info("LAPLACE_UNIVERSE_DIAGNOSTIC symbol={} high24h={} low24h={} range24hPct={} universeEligible={} universeRejectionReason={}",t.getSymbol(),t.getHighPrice(),t.getLowPrice(),range,eligible,rejection); }
 public boolean isReady(){return ready;} public Set<String> symbols(){return symbols;}
 public String sessionId(){return sessionId;} public BigDecimal startupVolume(String symbol){return startupVolumes.get(symbol);} public BigDecimal minimumVolumeThreshold(){return scannerProperties.getLiquidity().getMinQuoteVolume24h();}
}
