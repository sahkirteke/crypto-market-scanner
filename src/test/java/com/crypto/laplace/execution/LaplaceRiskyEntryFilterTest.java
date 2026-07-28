package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.laplace.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceRiskyEntryFilterTest {
 private final LaplaceRiskyEntryFilter filter=new LaplaceRiskyEntryFilter();
 @Test void atrBelowTwoIsAllowed(){assertThat(filter.isRisky(signal(1.9999,0.0))).isFalse();}
 @Test void exactAtrTwoAndZeroImbalanceIsSkipped(){assertThat(filter.isRisky(signal(2.0,0.0))).isTrue();}
 @Test void negativeImbalanceIsAllowed(){assertThat(filter.isRisky(signal(2.0,-0.001))).isFalse();}
 @Test void imbalanceBelowUpperBoundaryIsSkipped(){assertThat(filter.isRisky(signal(2.0,0.0999))).isTrue();}
 @Test void exactUpperBoundaryIsAllowed(){assertThat(filter.isRisky(signal(2.0,0.10))).isFalse();}
 private LaplaceSignalResult signal(double atr,double imbalance){Instant now=Instant.now();return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,2,2,atr,imbalance,.1,.1,.03,.04,2,LaplaceSignal.LONG,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of());}
}
