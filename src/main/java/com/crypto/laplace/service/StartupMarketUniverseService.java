package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.pool.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 private final LaplaceStrategyProperties properties;
 private final LaplaceCoinPoolRepository pool;
 private final LaplacePaperPositionRepository positions;
 private final AtomicBoolean attempted = new AtomicBoolean();
 private volatile Set<String> symbols = Set.of();
 private volatile Map<String, BigDecimal> startupVolumes = Map.of();
 private final String sessionId = UUID.randomUUID().toString();
 private volatile boolean ready;
 @Override public void run(ApplicationArguments args) { initialize(); }
 public synchronized void initialize() {
  if (!attempted.compareAndSet(false, true)) return;
  BigDecimal entry=properties.getLaplace().getVolumeScan().getEntryMinQuoteVolume();
  BigDecimal retention=properties.getLaplace().getVolumeScan().getRetentionMinQuoteVolume();
  log.info("LAPLACE_MARKET_UNIVERSE_INITIALIZATION_STARTED entryVolumeThreshold={} retentionVolumeThreshold={}",entry,retention);
  try {
   List<SymbolInfo> exchange=client.getExchangeInfo(); List<Ticker24h> tickers=client.getAll24hTickers();
   Set<String> tradable=new HashSet<>();
   for(SymbolInfo s:exchange)if(s!=null&&"USDT".equals(s.getQuoteAsset())&&"PERPETUAL".equals(s.getContractType())&&"TRADING".equals(s.getStatus()))tradable.add(s.getSymbol());
   Map<String,LaplaceCoinPoolEntity> members=pool.findAll().stream().filter(p->p.getState()!=LaplaceCoinPoolState.REMOVED).collect(Collectors.toMap(LaplaceCoinPoolEntity::getSymbol,Function.identity()));
   Set<String> protectedOpen=positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN).stream().map(p->p.getSymbol()).filter(tradable::contains).collect(Collectors.toSet());
   Set<String> eligible=new TreeSet<>();Map<String,BigDecimal> volumes=new HashMap<>();int newEligible=0,retained=0;
   for(Ticker24h t:tickers){if(t==null||!tradable.contains(t.getSymbol())||t.getQuoteVolume()==null)continue;boolean member=members.containsKey(t.getSymbol());boolean include=member?t.getQuoteVolume().compareTo(retention)>=0||protectedOpen.contains(t.getSymbol()):t.getQuoteVolume().compareTo(entry)>=0;if(include){eligible.add(t.getSymbol());volumes.put(t.getSymbol(),t.getQuoteVolume());if(member)retained++;else newEligible++;}}
   eligible.addAll(protectedOpen);
   symbols=Set.copyOf(eligible);startupVolumes=Map.copyOf(volumes);ready=!symbols.isEmpty();
   log.info("LAPLACE_MARKET_UNIVERSE_INITIALIZATION_COMPLETED candidateSymbols={} newEntryEligibleSymbols={} retainedPoolSymbols={} protectedOpenPositionSymbols={} totalStartupSymbols={}",tradable.size(),newEligible,retained,protectedOpen.size(),symbols.size());
   log.debug("LAPLACE_MARKET_UNIVERSE_SYMBOLS symbols={}",symbols);
   if(!ready)log.error("LAPLACE_MARKET_UNIVERSE_INITIALIZATION_EMPTY schedulerDisabled=true");
  }catch(RuntimeException e){ready=false;symbols=Set.of();startupVolumes=Map.of();log.error("LAPLACE_MARKET_UNIVERSE_INITIALIZATION_FAILED schedulerDisabled=true message={}",e.getMessage(),e);}
 }
 public boolean isReady(){return ready;}public Set<String> symbols(){return symbols;}
 public String sessionId(){return sessionId;}public BigDecimal startupVolume(String symbol){return startupVolumes.get(symbol);}public BigDecimal minimumVolumeThreshold(){return properties.getLaplace().getVolumeScan().getEntryMinQuoteVolume();}
}
