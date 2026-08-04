package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Kural4ExecutionDecisionService {
    private final LaplaceStrategyProperties properties;

    public Kural4ExecutionDecision decide(LaplaceSignalResult signal, LaplaceEntryDecision base,
                                           Kural4MarketContext context) {
        PositionSide raw = rawSide(signal.entrySignal());
        var cfg = properties.getLaplace().getKural4();
        if (!cfg.isEnabled() || !base.signalInverted()) return raw(raw, context);
        if (context == null) return new Kural4ExecutionDecision(false, raw, null,
                Kural4ExecutionAction.SKIP, List.of(Kural4DecisionReason.MARKET_CONTEXT_UNAVAILABLE), null);

        boolean extreme = signal.atrPercentage() >= cfg.getExtremeAtrSkipThreshold();
        boolean extB = cfg.isExtensionBEnabled() && raw == PositionSide.SHORT
                && signal.previous30mRawReturnPct() <= cfg.getExtensionBPrevious30mReturnMax()
                && context.btcRangePosition60() <= cfg.getExtensionBBtcRangePosition60mMax();
        boolean k4Long = raw == PositionSide.LONG
                && context.btcReturn30mPct() <= cfg.getLongInvertBtcReturn30mMax()
                && signal.atrPercentage() < cfg.getLongInvertAtrMaxExclusive();
        boolean k4Short = raw == PositionSide.SHORT
                && context.btcReturn15mPct() >= cfg.getShortInvertBtcReturn15mMin()
                && signal.aligned120mReturnPct() >= cfg.getShortInvertAligned120mMin()
                && signal.aligned120mReturnPct() <= cfg.getShortInvertAligned120mMax()
                && signal.atrPercentage() < cfg.getShortInvertAtrMaxExclusive();
        boolean extA = cfg.isExtensionAEnabled() && raw == PositionSide.LONG
                && signal.previousRawTakerImbalance() < cfg.getExtensionAPreviousImbalanceMaxExclusive()
                && signal.aligned120mReturnPct() <= cfg.getExtensionAAligned120mMax();
        if (extreme) return skip(raw, context, Kural4DecisionReason.K4_EXTREME_VOLATILITY_SKIP);
        if (extB) return skip(raw, context, Kural4DecisionReason.EXT_B_SKIP_RAW_SHORT);
        List<Kural4DecisionReason> reasons = new ArrayList<>();
        if (k4Long) reasons.add(Kural4DecisionReason.K4_INVERT_RAW_LONG);
        if (k4Short) reasons.add(Kural4DecisionReason.K4_INVERT_RAW_SHORT);
        if (extA) reasons.add(Kural4DecisionReason.EXT_A_INVERT_RAW_LONG);
        if (!reasons.isEmpty()) return new Kural4ExecutionDecision(true, raw, opposite(raw),
                Kural4ExecutionAction.INVERTED, reasons, context);
        return raw(raw, context);
    }

    private Kural4ExecutionDecision raw(PositionSide side, Kural4MarketContext context) {
        return new Kural4ExecutionDecision(side != null, side, side, Kural4ExecutionAction.RAW, List.of(), context);
    }
    private Kural4ExecutionDecision skip(PositionSide raw, Kural4MarketContext context, Kural4DecisionReason reason) {
        return new Kural4ExecutionDecision(false, raw, null, Kural4ExecutionAction.SKIP, List.of(reason), context);
    }
    private PositionSide rawSide(LaplaceSignal signal) {
        return signal == LaplaceSignal.LONG ? PositionSide.LONG : signal == LaplaceSignal.SHORT ? PositionSide.SHORT : null;
    }
    private PositionSide opposite(PositionSide side) { return side == PositionSide.LONG ? PositionSide.SHORT : PositionSide.LONG; }
}
