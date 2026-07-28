package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignalResult;
import org.springframework.stereotype.Component;

@Component
public class LaplaceRiskyEntryFilter {
    public boolean isRisky(LaplaceSignalResult signal) {
        return signal.atrPercentage() >= 2.0
                && signal.previousRawTakerImbalance() >= 0.0
                && signal.previousRawTakerImbalance() < 0.10;
    }
}
