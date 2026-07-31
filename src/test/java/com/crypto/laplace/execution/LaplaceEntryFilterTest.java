package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceEntryFilterTest {
 private final LaplaceEntryFilter filter=new LaplaceEntryFilter(new LaplaceRiskyEntryFilter());
 @Test void riskBoundariesAreExact(){assertThat(decision(PositionSide.SHORT,2,.0,50,50).riskyEntry()).isTrue();assertThat(decision(PositionSide.SHORT,2,.10,50,50).riskyEntry()).isFalse();}
 @Test void longRequiresBothBreadthsAtBoundary(){assertThat(decision(PositionSide.LONG,1,.1,52.5,52.5).entryAllowed()).isTrue();assertThat(decision(PositionSide.LONG,1,.1,52.49,70).rejectionReason()).isEqualTo("LONG_MARKET_NOT_RISING");assertThat(decision(PositionSide.LONG,1,.1,70,52.49).entryAllowed()).isFalse();}
 @Test void shortOnlyBlocksBullAcceleration(){assertThat(decision(PositionSide.SHORT,1,.1,60,50).entryAllowed()).isFalse();assertThat(decision(PositionSide.SHORT,1,.1,59.99,40).entryAllowed()).isTrue();assertThat(decision(PositionSide.SHORT,1,.1,70,60.01).entryAllowed()).isTrue();assertThat(decision(PositionSide.SHORT,1,.1,70,68).entryAllowed()).isTrue();}
 @Test void effectiveRatherThanRawSideSelectsFilter(){assertThat(filter.evaluate(signal(LaplaceSignal.LONG,1,.1),PositionSide.SHORT,snapshot(70,68)).entryAllowed()).isTrue();assertThat(filter.evaluate(signal(LaplaceSignal.SHORT,1,.1),PositionSide.LONG,snapshot(61,49)).entryAllowed()).isFalse();}
 private LaplaceEntryFilter.Decision decision(PositionSide side,double atr,double imbalance,double b2,double b4){return filter.evaluate(signal(LaplaceSignal.LONG,atr,imbalance),side,snapshot(b2,b4));}
 private LaplaceMarketBreadthSnapshot snapshot(double b2,double b4){return new LaplaceMarketBreadthSnapshot(Instant.EPOCH,Instant.EPOCH,b2,b4,1,1,1,1);}
 private LaplaceSignalResult signal(LaplaceSignal raw,double atr,double imbalance){return new LaplaceSignalResult("s","v","X","30m","L",14,"C",false,Instant.EPOCH,Instant.EPOCH,100,0,0,0,0,0,0,0,atr,imbalance,0,0,0,0,2,raw,raw,StartupState.ACTIVE,1,true,List.of());}
}
