package com.crypto.laplace.audit;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class VolumeScanAuditJsonlWriter implements ApplicationRunner {
    private final VolumeScanAuditOutboxRepository repository;
    private final LaplaceStrategyProperties properties;
    private final ReentrantLock fileLock = new ReentrantLock();

    @Override public void run(ApplicationArguments args) { drain(); }

    public void drain() {
        fileLock.lock();
        try {
            Path path = Path.of(properties.getLaplace().getVolumeScan().getAuditJsonlPath());
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            for (VolumeScanAuditOutboxEntity event : repository.findTop500ByJsonlWrittenFalseOrderByCreatedAtAsc()) {
                Files.writeString(path, event.getPayloadJson() + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                event.setJsonlWritten(true);
                event.setWrittenAt(Instant.now());
                repository.save(event);
            }
        } catch (IOException e) {
            log.error("LAPLACE_VOLUME_AUDIT_JSONL_WRITE_FAILED pendingEventsPreserved=true", e);
        } finally { fileLock.unlock(); }
    }
}
