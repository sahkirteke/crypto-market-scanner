package com.crypto.laplace.execution;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.service.StartupMarketUniverseService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Slf4j @Service @Order(110)
@ConditionalOnProperty(prefix="trading.laplace",name="enabled",havingValue="true")
public class Kural3WarmupService implements ApplicationRunner {
    private static final long[] BACKOFF_MS={300,700};
    private final StartupMarketUniverseService universe;private final LaplaceVolumeProfileService profiles;private final LaplaceStrategyProperties properties;
    private final ConcurrentMap<String,Kural3WarmupStatus> statuses=new ConcurrentHashMap<>();private volatile ExecutorService executor;
    public Kural3WarmupService(StartupMarketUniverseService universe,LaplaceVolumeProfileService profiles,LaplaceStrategyProperties properties){this.universe=universe;this.profiles=profiles;this.properties=properties;}
    @Override public void run(ApplicationArguments args){prewarm(universe.symbols());}
    public void prewarm(Set<String> activeEntryUniverse){if(activeEntryUniverse==null||activeEntryUniverse.isEmpty())return;ensureExecutor();activeEntryUniverse.forEach(symbol->{if(statuses.putIfAbsent(symbol,Kural3WarmupStatus.PENDING)==null)executor.submit(()->warm(symbol));});}
    public Kural3WarmupStatus status(String symbol){return statuses.getOrDefault(symbol,Kural3WarmupStatus.PENDING);}
    public void retryFailed(String symbol){if(statuses.replace(symbol,Kural3WarmupStatus.FAILED,Kural3WarmupStatus.PENDING)){ensureExecutor();executor.submit(()->warm(symbol));}}
    private void warm(String symbol){int max=Math.max(1,properties.getLaplace().getMaxWarmupAttempts());for(int attempt=1;attempt<=max;attempt++){long started=System.nanoTime();try{var result=profiles.prewarm(symbol,Instant.now());statuses.put(symbol,Kural3WarmupStatus.READY);log.info("LAPLACE_KURAL3_WARMUP symbol={} activeUniverse=true warmupStatus=READY nySessionDate={} requestedSessionCandleCount={} loadedSessionCandleCount={} requestedCurrentCandleCount={} loadedCurrentCandleCount={} profileCacheHit={} profileCacheMiss={} warmupAttempt={} warmupDurationMs={} warmupFailureReason={}",symbol,result.nySessionDate(),LaplaceVolumeProfileService.NY_SESSION_5M_CANDLE_COUNT,result.loadedSessionCandleCount(),LaplaceVolumeProfileService.CURRENT_DELTA_60M_CANDLE_COUNT,result.loadedCurrentCandleCount(),result.profileCacheHit(),result.profileCacheMiss(),attempt,result.durationMs(),null);return;}catch(RuntimeException failure){log.warn("LAPLACE_KURAL3_WARMUP symbol={} activeUniverse=true warmupStatus={} requestedSessionCandleCount={} requestedCurrentCandleCount={} warmupAttempt={} warmupDurationMs={} warmupFailureReason={}",symbol,attempt==max?"FAILED":"PENDING",LaplaceVolumeProfileService.NY_SESSION_5M_CANDLE_COUNT,LaplaceVolumeProfileService.CURRENT_DELTA_60M_CANDLE_COUNT,attempt,(System.nanoTime()-started)/1_000_000,failure.getMessage());if(attempt<max)sleep(BACKOFF_MS[Math.min(attempt-1,BACKOFF_MS.length-1)]);}}
        statuses.put(symbol,Kural3WarmupStatus.FAILED);
    }
    private synchronized void ensureExecutor(){if(executor==null)executor=Executors.newFixedThreadPool(Math.max(1,properties.getLaplace().getVolumeProfileWarmupConcurrency()),Thread.ofPlatform().name("kural3-warmup-",0).factory());}
    private void sleep(long millis){try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    @PreDestroy void shutdown(){if(executor!=null)executor.shutdownNow();}
}
