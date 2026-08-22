package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Routes the immutable raw result to the sole production paper variant. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceSignalFanOut {
    private final LaplacePaperTradeCoordinator invertedTrue;

    public void initializeBaseline(String symbol, LaplaceSignal raw) {
        independently("INVERTED_TRUE", () -> invertedTrue.initializeBaseline(symbol, raw));
    }

    public void onSignal(LaplaceSignalResult raw, boolean inEntryUniverse) {
        independently("INVERTED_TRUE", () -> invertedTrue.onSignal(raw, inEntryUniverse));
    }

    public Set<String> managementSymbols() {
        return invertedTrue.managementSymbols();
    }

    public void clearRuntimeState() {
        invertedTrue.clearRuntimeState();
    }

    private void independently(String variant, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            log.error("LAPLACE_VARIANT_EXECUTION_FAILED variant={}", variant, failure);
        }
    }
}
