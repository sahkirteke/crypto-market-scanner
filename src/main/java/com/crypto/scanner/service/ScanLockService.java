package com.crypto.scanner.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class ScanLockService {
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean tryAcquire() {
        return running.compareAndSet(false, true);
    }

    public void release() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }
}
