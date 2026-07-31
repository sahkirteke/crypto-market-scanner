package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.laplace.model.*;
import com.crypto.common.enums.PositionSide;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceEntryDecisionServiceTest {
    private final LaplaceEntryDecisionService service = new LaplaceEntryDecisionService(new LaplaceRiskyEntryFilter());

    @Test void exactPositiveBoundaryIsAllowed() { assertAllowed(signal(1, .10, .04, .04)); }
    @Test void exactNegativeBoundaryIsAllowed() { assertAllowed(signal(1, .10, -.04, -.04)); }
    @Test void riskyOnlyHasRiskReason() { assertReasons(signal(2, .05, .06, .05), LaplaceEntrySkipReason.RISKY_ENTRY); }
    @Test void weakCurrentOnlyHasSlopeReason() { assertReasons(signal(1, .10, .039999, .08), LaplaceEntrySkipReason.WEAK_CURRENT_SLOPE); }
    @Test void weakPreviousOnlyHasSlopeReason() { assertReasons(signal(1, .10, .08, .039999), LaplaceEntrySkipReason.WEAK_PREVIOUS_SLOPE); }
    @Test void bothFailuresAreRetainedInEnumOrder() { assertReasons(signal(2.5, .05, .035, .031), LaplaceEntrySkipReason.RISKY_ENTRY, LaplaceEntrySkipReason.WEAK_BOTH_SLOPES); }
    @Test void negativeShortSlopesUseAbsoluteStrength() { assertAllowed(signal(1, .10, -.05, -.06)); }
    @Test void qualifyingRawLongStaysLong() { assertDirection(featureSignal(LaplaceSignal.LONG,.05,.30,0,0),PositionSide.LONG,false); }
    @Test void rawLongBelowImbalanceIsInvertedShort() { assertDirection(featureSignal(LaplaceSignal.LONG,.0499,.30,0,0),PositionSide.SHORT,true); }
    @Test void rawLongAboveReturnLimitIsInvertedShort() { assertDirection(featureSignal(LaplaceSignal.LONG,.05,.3001,0,0),PositionSide.SHORT,true); }
    @Test void qualifyingRawShortStaysShort() { assertDirection(featureSignal(LaplaceSignal.SHORT,0,0,.90,2),PositionSide.SHORT,false); }
    @Test void imbalanceAloneNeverQualifiesRawShort() { assertDirection(featureSignal(LaplaceSignal.SHORT,.32,0,.89,2),PositionSide.LONG,true); }
    @Test void noExceptionInvertsRawSignal() { assertDirection(featureSignal(LaplaceSignal.SHORT,0,0,.89,2.01),PositionSide.LONG,true); }

    private void assertAllowed(LaplaceSignalResult signal) { var decision=service.decide(signal); assertThat(decision.entryAllowed()).isTrue(); assertThat(decision.skipReasons()).isEmpty(); }
    private void assertReasons(LaplaceSignalResult signal,LaplaceEntrySkipReason... reasons) { var decision=service.decide(signal); assertThat(decision.entryAllowed()).isFalse(); assertThat(decision.skipReasons()).containsExactly(reasons); }
    private void assertDirection(LaplaceSignalResult signal,PositionSide side,boolean inverted){var decision=service.decide(signal);assertThat(decision.effectiveExecutionSide()).isEqualTo(side);assertThat(decision.signalInverted()).isEqualTo(inverted);}
    private LaplaceSignalResult featureSignal(LaplaceSignal raw,double imbalance,double ret120,double previous30,double aligned120){Instant now=Instant.parse("2026-07-30T00:00:00Z");return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,2,2,1,imbalance,ret120,previous30,aligned120,.1,.1,.03,.04,2,raw,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of());}
    private LaplaceSignalResult signal(double atr,double imbalance,double current,double previous) { Instant now=Instant.parse("2026-07-30T00:00:00Z"); return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,2,2,atr,imbalance,current,previous,.03,.04,2,LaplaceSignal.SHORT,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of()); }
}
