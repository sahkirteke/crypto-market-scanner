package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceDualVariantTest {
 @Test void trueDirectionMappingRemainsContrarian(){var directions=new LaplaceDirectionMapper();assertThat(directions.map(LaplacePaperVariant.INVERTED_TRUE,LaplaceSignal.LONG)).isEqualTo(PositionSide.SHORT);assertThat(directions.map(LaplacePaperVariant.INVERTED_TRUE,LaplaceSignal.SHORT)).isEqualTo(PositionSide.LONG);}
 @Test void rawSignalIsDeliveredOnlyToTrueCoordinator(){var trueEngine=mock(LaplacePaperTradeCoordinator.class);var router=new LaplaceSignalFanOut(trueEngine);var raw=signal();router.onSignal(raw,true);verify(trueEngine).onSignal(same(raw),eq(true));}
 private LaplaceSignalResult signal(){Instant now=Instant.parse("2026-08-14T00:00:00Z");return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,100,99,98,97,1,1,10,10,.1,.1,.03,.04,2,LaplaceSignal.LONG,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of());}
}
