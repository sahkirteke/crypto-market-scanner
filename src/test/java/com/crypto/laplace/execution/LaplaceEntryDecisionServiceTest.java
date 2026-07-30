package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.laplace.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceEntryDecisionServiceTest {
    private final LaplaceEntryDecisionService service = new LaplaceEntryDecisionService(new LaplaceRiskyEntryFilter());

    @Test void exactPositiveBoundaryIsAllowed() { assertAllowed(signal(1, .10, .04, .04)); }
    @Test void exactNegativeBoundaryIsAllowed() { assertAllowed(signal(1, .10, -.04, -.04)); }
    @Test void riskyOnlyHasRiskReason() { assertReasons(signal(2, .05, .06, .05), LaplaceEntrySkipReason.RISKY_ENTRY_FILTER); }
    @Test void weakCurrentOnlyHasSlopeReason() { assertReasons(signal(1, .10, .039999, .08), LaplaceEntrySkipReason.SLOPE_STRENGTH_FILTER); }
    @Test void weakPreviousOnlyHasSlopeReason() { assertReasons(signal(1, .10, .08, .039999), LaplaceEntrySkipReason.SLOPE_STRENGTH_FILTER); }
    @Test void bothFailuresAreRetainedInEnumOrder() { assertReasons(signal(2.5, .05, .035, .031), LaplaceEntrySkipReason.RISKY_ENTRY_FILTER, LaplaceEntrySkipReason.SLOPE_STRENGTH_FILTER); }
    @Test void negativeShortSlopesUseAbsoluteStrength() { assertAllowed(signal(1, .10, -.05, -.06)); }

    private void assertAllowed(LaplaceSignalResult signal) { var decision=service.decide(signal); assertThat(decision.entryAllowed()).isTrue(); assertThat(decision.skipReasons()).isEmpty(); }
    private void assertReasons(LaplaceSignalResult signal,LaplaceEntrySkipReason... reasons) { var decision=service.decide(signal); assertThat(decision.entryAllowed()).isFalse(); assertThat(decision.skipReasons()).containsExactly(reasons); }
    private LaplaceSignalResult signal(double atr,double imbalance,double current,double previous) { Instant now=Instant.parse("2026-07-30T00:00:00Z"); return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,2,2,atr,imbalance,current,previous,.03,.04,2,LaplaceSignal.SHORT,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of()); }
}
