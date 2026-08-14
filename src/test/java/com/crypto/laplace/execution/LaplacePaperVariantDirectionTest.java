package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import org.junit.jupiter.api.Test;

class LaplacePaperVariantDirectionTest {
    @Test void variantsMapLongAndShortIndependently() {
        var trueCoordinator=new LaplacePaperTradeCoordinator(new LaplaceStrategyProperties(),mock(com.crypto.laplace.persistence.LaplacePaperPositionRepository.class),mock(LaplacePaperExecutionService.class),mock(LaplaceTradeJsonlWriter.class),mock(com.crypto.laplace.service.LaplaceRuntimeService.class));
        var falseCoordinator=new LaplaceInvertedFalseTradeCoordinator(mock(LaplaceInvertedFalsePositionRepository.class),mock(LaplaceInvertedFalseExecutionService.class),mock(LaplaceInvertedFalseRuntimeService.class));
        assertThat(trueCoordinator.mapRawSignalToExecutionSide(LaplaceSignal.LONG)).isEqualTo(PositionSide.SHORT);
        assertThat(falseCoordinator.mapRawSignalToExecutionSide(LaplaceSignal.LONG)).isEqualTo(PositionSide.LONG);
        assertThat(trueCoordinator.mapRawSignalToExecutionSide(LaplaceSignal.SHORT)).isEqualTo(PositionSide.LONG);
        assertThat(falseCoordinator.mapRawSignalToExecutionSide(LaplaceSignal.SHORT)).isEqualTo(PositionSide.SHORT);
    }
}
