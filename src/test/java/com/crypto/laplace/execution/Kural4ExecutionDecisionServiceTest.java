package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Kural4ExecutionDecisionServiceTest {
    private LaplaceStrategyProperties properties;
    private Kural4ExecutionDecisionService service;
    private final Kural4MarketContext neutral = context(0, 0, .5);

    @BeforeEach void setUp() { properties=new LaplaceStrategyProperties(); service=new Kural4ExecutionDecisionService(properties); }
    @Test void disabledIsRawWithoutContext(){properties.getLaplace().getKural4().setEnabled(false);assertRaw(decide(signal(LaplaceSignal.LONG,1,.1,.3,.1),true,null),PositionSide.LONG);}
    @Test void legacyNotInvertedIsRawWithoutContext(){assertRaw(decide(signal(LaplaceSignal.SHORT,1,.1,.3,.1),false,null),PositionSide.SHORT);}
    @Test void atrExactly430Skips(){assertSkip(decide(signal(LaplaceSignal.LONG,4.30,.1,.3,.1),true,neutral),Kural4DecisionReason.K4_EXTREME_VOLATILITY_SKIP);}
    @Test void atrAbove430Skips(){assertSkip(decide(signal(LaplaceSignal.LONG,4.31,.1,.3,.1),true,neutral),Kural4DecisionReason.K4_EXTREME_VOLATILITY_SKIP);}
    @Test void atrBelow430DoesNotExtremeSkip(){assertThat(decide(signal(LaplaceSignal.LONG,4.29,.1,.3,.1),true,neutral).reasons()).doesNotContain(Kural4DecisionReason.K4_EXTREME_VOLATILITY_SKIP);}
    @Test void longBoundaryBtc30Inverts(){assertInverted(decide(signal(LaplaceSignal.LONG,2.99,.1,.3,.1),true,context(0,-.03,.5)),PositionSide.SHORT,Kural4DecisionReason.K4_INVERT_RAW_LONG);}
    @Test void longBtc30AboveBoundaryIsRaw(){assertRaw(decide(signal(LaplaceSignal.LONG,2.99,.1,.3,.1),true,context(0,-.029,.5)),PositionSide.LONG);}
    @Test void longAtrExactly300DoesNotK4Invert(){assertThat(decide(signal(LaplaceSignal.LONG,3.00,.1,.3,.1),true,context(0,-.03,.5)).reasons()).doesNotContain(Kural4DecisionReason.K4_INVERT_RAW_LONG);}
    @Test void shortLowerBoundariesInvert(){assertInverted(decide(signal(LaplaceSignal.SHORT,4.29,.1,.50,.1),true,context(.05,0,.5)),PositionSide.LONG,Kural4DecisionReason.K4_INVERT_RAW_SHORT);}
    @Test void shortAlignedUpperBoundaryInverts(){assertInverted(decide(signal(LaplaceSignal.SHORT,4.29,.1,1.00,.1),true,context(.05,0,.5)),PositionSide.LONG,Kural4DecisionReason.K4_INVERT_RAW_SHORT);}
    @Test void shortAboveAlignedUpperBoundaryIsRaw(){assertRaw(decide(signal(LaplaceSignal.SHORT,4.29,.1,1.001,.1),true,context(.05,0,.5)),PositionSide.SHORT);}
    @Test void extensionABoundariesInvert(){assertInverted(decide(signal(LaplaceSignal.LONG,3.5,-.001,.25,.1),true,neutral),PositionSide.SHORT,Kural4DecisionReason.EXT_A_INVERT_RAW_LONG);}
    @Test void extensionBBoundariesSkip(){assertSkip(decide(signal(LaplaceSignal.SHORT,3,.1,.3,0),true,context(0,0,.20)),Kural4DecisionReason.EXT_B_SKIP_RAW_SHORT);}
    @Test void extensionBHasPriorityOverShortInversion(){var d=decide(signal(LaplaceSignal.SHORT,3,.1,.50,0),true,context(.05,0,.20));assertSkip(d,Kural4DecisionReason.EXT_B_SKIP_RAW_SHORT);assertThat(d.reasons()).doesNotContain(Kural4DecisionReason.K4_INVERT_RAW_SHORT);}
    @Test void allMatchedInversionReasonsAreAudited(){var d=decide(signal(LaplaceSignal.LONG,2.99,-.01,.25,.1),true,context(0,-.03,.5));assertThat(d.reasons()).containsExactly(Kural4DecisionReason.K4_INVERT_RAW_LONG,Kural4DecisionReason.EXT_A_INVERT_RAW_LONG);}

    private Kural4ExecutionDecision decide(LaplaceSignalResult s,boolean inverted,Kural4MarketContext c){return service.decide(s,new LaplaceEntryDecision(true,false,true,false,false,inverted,inverted?(s.entrySignal()==LaplaceSignal.LONG?PositionSide.SHORT:PositionSide.LONG):(s.entrySignal()==LaplaceSignal.LONG?PositionSide.LONG:PositionSide.SHORT),List.of()),c);}
    private void assertRaw(Kural4ExecutionDecision d,PositionSide side){assertThat(d.action()).isEqualTo(Kural4ExecutionAction.RAW);assertThat(d.finalExecutionSide()).isEqualTo(side);}
    private void assertSkip(Kural4ExecutionDecision d,Kural4DecisionReason reason){assertThat(d.entryAllowed()).isFalse();assertThat(d.action()).isEqualTo(Kural4ExecutionAction.SKIP);assertThat(d.finalExecutionSide()).isNull();assertThat(d.reasons()).containsExactly(reason);}
    private void assertInverted(Kural4ExecutionDecision d,PositionSide side,Kural4DecisionReason reason){assertThat(d.action()).isEqualTo(Kural4ExecutionAction.INVERTED);assertThat(d.finalExecutionSide()).isEqualTo(side);assertThat(d.reasons()).contains(reason);}
    private Kural4MarketContext context(double r15,double r30,double range){Instant t=Instant.parse("2026-01-01T01:00:00Z");return new Kural4MarketContext(t,t,100,r15,r30,range,20);}
    private LaplaceSignalResult signal(LaplaceSignal side,double atr,double imbalance,double aligned,double previousReturn){Instant t=Instant.parse("2026-01-01T01:00:00Z");return new LaplaceSignalResult("s","v","ETHUSDT","30m","LAPLACE",14,"CLOSE",false,t.minusSeconds(1800),t,100,3,2,1,1,1,1,1,atr,imbalance,.1,previousReturn,aligned,.1,.1,.1,.1,2,side,side,StartupState.ACTIVE,1,true,List.of());}
}
