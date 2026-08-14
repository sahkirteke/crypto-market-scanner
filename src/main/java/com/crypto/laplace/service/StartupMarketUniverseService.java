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
 private final LaplaceMarketDataGate marketDataGate;
 private final AtomicBoolean attempted = new AtomicBoolean();
 private volatile Set<String> symbols = Set.of();
 private volatile Map<String, BigDecimal> startupVolumes = Map.of();
 private volatile String sessionId = UUID.randomUUID().toString();
 private volatile boolean ready;
 @Override public void run(ApplicationArguments args) { if(marketDataGate.allowsMarketData())initialize(); }
 public synchronized void initialize() {
  if(!marketDataGate.allowsMarketData())return;
  if (!attempted.compareAndSet(false, true)) return;
  BigDecimal threshold=scannerProperties.getLiquidity().getMinQuoteVolume24h();
  log.info("MARKET_UNIVERSE_INITIALIZATION_STARTED minimumVolumeThreshold={}", threshold);
  try {
   List<SymbolInfo> exchange=client.getExchangeInfo(); List<Ticker24h> tickers=client.getAll24hTickers();
   Set<String> tradable=new HashSet<>();
   for (SymbolInfo s: exchange) if (s!=null && "USDT".equals(s.getQuoteAsset()) && "PERPETUAL".equals(s.getContractType()) && "TRADING".equals(s.getStatus())) tradable.add(s.getSymbol());
   Set<String> eligible=new TreeSet<>(); Map<String, BigDecimal> volumes=new HashMap<>();
   for (Ticker24h t: tickers) if (t!=null && tradable.contains(t.getSymbol()) && t.getQuoteVolume()!=null && t.getQuoteVolume().compareTo(threshold)>=0) { eligible.add(t.getSymbol()); volumes.put(t.getSymbol(),t.getQuoteVolume()); }
   symbols=Set.copyOf(eligible); startupVolumes=Map.copyOf(volumes); ready=!symbols.isEmpty();
   log.info("MARKET_UNIVERSE_INITIALIZATION_COMPLETED totalSymbols={} eligibleSymbols={} minimumVolumeThreshold={}", tradable.size(), symbols.size(), threshold);
   log.debug("MARKET_UNIVERSE_SYMBOLS symbols={}", symbols);
   if (!ready) log.error("MARKET_UNIVERSE_INITIALIZATION_EMPTY schedulerDisabled=true");
  } catch (RuntimeException e) { ready=false; symbols=Set.of(); startupVolumes=Map.of(); log.error("MARKET_UNIVERSE_INITIALIZATION_FAILED schedulerDisabled=true message={}", e.getMessage(), e); }
 }
 public boolean isReady(){return ready;} public Set<String> symbols(){return symbols;}
 public synchronized void clear(){symbols=Set.of();startupVolumes=Map.of();ready=false;attempted.set(false);sessionId=UUID.randomUUID().toString();}
 public String sessionId(){return sessionId;} public BigDecimal startupVolume(String symbol){return startupVolumes.get(symbol);} public BigDecimal minimumVolumeThreshold(){return scannerProperties.getLiquidity().getMinQuoteVolume24h();}
}
