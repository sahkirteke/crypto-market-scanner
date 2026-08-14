package com.crypto.laplace.service;

import static org.mockito.Mockito.*;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.scheduler.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.stream.Stream;

class LaplaceTemporaryStateResetIsolationTest {
 private LaplacePaperTradeCoordinator trueCoordinator;private LaplaceInvertedFalseTradeCoordinator falseCoordinator;private LaplaceStopLossScheduler trueSl;private LaplaceInvertedFalseStopLossScheduler falseSl;private LaplaceTemporaryStateResetService reset;
 @BeforeEach void setup(){trueCoordinator=mock(LaplacePaperTradeCoordinator.class);falseCoordinator=mock(LaplaceInvertedFalseTradeCoordinator.class);trueSl=mock(LaplaceStopLossScheduler.class);falseSl=mock(LaplaceInvertedFalseStopLossScheduler.class);ObjectProvider<CacheManager> caches=mock(ObjectProvider.class);when(caches.orderedStream()).thenReturn(Stream.empty());reset=new LaplaceTemporaryStateResetService(mock(StartupMarketUniverseService.class),mock(LaplaceStartupHistoryService.class),trueCoordinator,mock(LaplaceThirtyMinuteScheduler.class),trueSl,caches,mock(LaplaceMarketDataGate.class));ReflectionTestUtils.setField(reset,"invertedFalseCoordinator",falseCoordinator);ReflectionTestUtils.setField(reset,"invertedFalseStopLossScheduler",falseSl);}
 @Test void trueResetDoesNotTouchFalseCoordinatorSlOrCaches(){reset.clearInvertedTrue();verify(trueCoordinator).clearRuntimeState();verify(trueSl).clearRuntimeState();verifyNoInteractions(falseCoordinator,falseSl);}
 @Test void falseResetDoesNotTouchTrueCoordinatorSlOrCaches(){reset.clearInvertedFalse();verify(falseCoordinator).clearRuntimeState();verify(falseSl).clearRuntimeState();verifyNoInteractions(trueCoordinator,trueSl);}
}
