package com.crypto.laplace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.api.controller.PaperPositionController;
import com.crypto.api.dto.LaplaceOpenPaperPositionsResponse;
import com.crypto.api.mapper.PaperPositionApiMapper;
import com.crypto.laplace.model.LaplacePaperVariant;
import com.crypto.paper.service.*;
import com.crypto.persistence.repository.PaperPositionEventRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyPaperOpenEndpointTest {
 @Test void legacyOpenEndpointReadsOnlyTrueVariant(){var variants=mock(LaplaceVariantApiService.class);var expected=new LaplaceOpenPaperPositionsResponse(List.of(),0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);when(variants.open(LaplacePaperVariant.INVERTED_TRUE)).thenReturn(expected);var controller=new PaperPositionController(mock(PaperPositionService.class),mock(ExitEngineService.class),mock(PaperPositionQueryService.class),mock(PaperPositionManualCloseService.class),mock(PaperPositionApiMapper.class),mock(PaperPositionEventRepository.class),mock(LaplacePaperApiService.class),variants);assertThat(controller.getOpenPositions()).isSameAs(expected);verify(variants).open(LaplacePaperVariant.INVERTED_TRUE);}
}
