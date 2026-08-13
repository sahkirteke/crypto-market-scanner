package com.crypto.laplace.scheduler;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Session-owned mutable scheduler state, separated from the scheduled Spring bean to avoid lifecycle cycles. */
@Component
public class LaplaceThirtyMinuteRuntimeState {
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentHashMap<String, Instant> lastProcessed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> postStartupBarCounts = new ConcurrentHashMap<>();

    public AtomicBoolean running() { return running; }
    public ConcurrentHashMap<String, Instant> lastProcessed() { return lastProcessed; }
    public ConcurrentHashMap<String, Integer> postStartupBarCounts() { return postStartupBarCounts; }
    public void resetForSession() { running.set(false); lastProcessed.clear(); postStartupBarCounts.clear(); }
}
