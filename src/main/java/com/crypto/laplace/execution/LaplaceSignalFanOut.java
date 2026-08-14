package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Distributes the exact same immutable raw result instance to both isolated runtimes. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceSignalFanOut {
    private final LaplacePaperTradeCoordinator invertedTrue;
    private final LaplaceInvertedFalseTradeCoordinator invertedFalse;

    public void initializeBaseline(String symbol, LaplaceSignal raw) {
        independently("INVERTED_TRUE", () -> invertedTrue.initializeBaseline(symbol, raw));
        independently("INVERTED_FALSE", () -> invertedFalse.initializeBaseline(symbol, raw));
    }

    public void onSignal(LaplaceSignalResult raw, boolean inEntryUniverse) {
        independently("INVERTED_TRUE", () -> invertedTrue.onSignal(raw, inEntryUniverse));
        independently("INVERTED_FALSE", () -> invertedFalse.onSignal(raw, inEntryUniverse));
    }

    public Set<String> managementSymbols() {
        Set<String> result = new HashSet<>(invertedTrue.managementSymbols());
        result.addAll(invertedFalse.managementSymbols());
        return Set.copyOf(result);
    }

    public void clearRuntimeState() {
        invertedTrue.clearRuntimeState();
        invertedFalse.clearRuntimeState();
    }

    private void independently(String variant, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            log.error("LAPLACE_VARIANT_EXECUTION_FAILED variant={}", variant, failure);
        }
    }
}
