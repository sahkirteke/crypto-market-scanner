package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplaceMarketBreadthSnapshot;
import com.crypto.laplace.model.LaplaceSignalResult;
import org.springframework.stereotype.Component;

@Component
public class LaplaceEntryFilter {
    private final LaplaceRiskyEntryFilter risk;

    public LaplaceEntryFilter(LaplaceRiskyEntryFilter risk) { this.risk = risk; }

    public Decision evaluate(LaplaceSignalResult signal, PositionSide effectiveSide,
                             LaplaceMarketBreadthSnapshot breadth) {
        boolean riskyEntry = risk.isRisky(signal);
        boolean available = breadth != null && breadth.available();
        double breadth2h = available ? breadth.marketBreadth2h() : Double.NaN;
        double breadth4h = available ? breadth.marketBreadth4h() : Double.NaN;
        double acceleration = breadth2h - breadth4h;
        boolean marketRising = available && breadth2h >= 52.5 && breadth4h >= 52.5;
        boolean shortBullRisk = available && breadth2h >= 60.0 && acceleration >= 10.0;
        boolean longAllowed = effectiveSide == PositionSide.LONG && marketRising;
        boolean shortAllowed = effectiveSide == PositionSide.SHORT && !shortBullRisk;
        boolean allowed = available && !riskyEntry && (longAllowed || shortAllowed);
        String reason = allowed ? null : !available ? "MARKET_BREADTH_UNAVAILABLE" : riskyEntry ? "RISKY_ENTRY_FILTER"
                : effectiveSide == PositionSide.LONG ? "LONG_MARKET_NOT_RISING" : "SHORT_BULL_ACCELERATION";
        return new Decision(allowed, reason, riskyEntry, marketRising, shortBullRisk, acceleration);
    }

    public record Decision(boolean entryAllowed, String rejectionReason, boolean riskyEntry,
                           boolean marketRising, boolean shortBullRisk, double marketBreadthAcceleration) {}
}
