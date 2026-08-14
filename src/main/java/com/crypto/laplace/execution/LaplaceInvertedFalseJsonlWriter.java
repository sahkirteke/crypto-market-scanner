package com.crypto.laplace.execution;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.persistence.LaplaceInvertedFalseTradeEventRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceInvertedFalseJsonlWriter {
    private final LaplaceInvertedFalseTradeEventRepository events;
    private final LaplaceStrategyProperties properties;
    public void drain() {
        for (var event : events.findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc()) try {
            Path directory = Path.of(properties.getLaplace().getPaper().getInvertedFalse().getTradeDirectory());
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(event.getSymbol()+".jsonl"), event.getPayloadJson()+System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            event.setJsonlWritten(true); events.save(event);
        } catch (Exception ignored) { /* retained in the outbox for the next independent retry */ }
    }
}
