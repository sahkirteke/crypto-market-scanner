package com.crypto.laplace.execution;

import static org.mockito.Mockito.*;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.service.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceCooldownSignalIsolationTest {
 @Test void trueCooldownStillLetsFalseConsumeRawSignal(){Harness h=new Harness(false,true);h.fanOut.initializeBaseline("BTCUSDT",LaplaceSignal.NONE);h.fanOut.onSignal(h.raw,true);verify(h.falseExecution).open(same(h.raw),eq(PositionSide.LONG),isNull(),eq("FLAT"));verifyNoInteractions(h.trueExecution);}
 @Test void falseCooldownStillLetsTrueConsumeRawSignal(){Harness h=new Harness(true,false);h.fanOut.initializeBaseline("BTCUSDT",LaplaceSignal.NONE);h.fanOut.onSignal(h.raw,true);verify(h.trueExecution).open(same(h.raw),eq(PositionSide.SHORT),isNull(),eq("FLAT"));verifyNoInteractions(h.falseExecution);}
 @Test void firstSignalAfterClearedRuntimeIsBaselineOnly(){Harness h=new Harness(false,true);h.fanOut.onSignal(h.raw,true);verifyNoInteractions(h.falseExecution);}
 private static LaplaceSignalResult signal(){Instant now=Instant.parse("2026-08-14T00:00:00Z");return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,10,10,.1,.1,.03,.04,2,LaplaceSignal.LONG,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of());}
 private static class Harness{final LaplaceSignalResult raw=signal();final LaplacePaperExecutionService trueExecution=mock(LaplacePaperExecutionService.class);final LaplaceInvertedFalseExecutionService falseExecution=mock(LaplaceInvertedFalseExecutionService.class);final LaplaceSignalFanOut fanOut;Harness(boolean trueActive,boolean falseActive){var config=new LaplaceStrategyProperties();var trueRepo=mock(LaplacePaperPositionRepository.class);var falseRepo=mock(LaplaceInvertedFalsePositionRepository.class);when(trueRepo.findByStrategyAndSymbolAndStatus(any(),any(),any())).thenReturn(List.of());when(falseRepo.findByStrategyAndSymbolAndStatus(any(),any(),any())).thenReturn(List.of());var tr=mock(LaplaceRuntimeService.class);var fr=mock(LaplaceInvertedFalseRuntimeService.class);when(tr.isActive()).thenReturn(trueActive);when(fr.isActive()).thenReturn(falseActive);var tc=new LaplacePaperTradeCoordinator(config,trueRepo,trueExecution,mock(LaplaceTradeJsonlWriter.class),tr);var fc=new LaplaceInvertedFalseTradeCoordinator(config,falseRepo,falseExecution,mock(LaplaceInvertedFalseTradeJsonlWriter.class),fr);fanOut=new LaplaceSignalFanOut(tc,fc);}}
}
