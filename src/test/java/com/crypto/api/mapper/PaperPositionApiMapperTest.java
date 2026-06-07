package com.crypto.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaperPositionApiMapperTest {
    private final PaperPositionApiMapper mapper = new PaperPositionApiMapper(new JsonTextMapper(new ObjectMapper()));

    @Test
    void toResponseUsesSingleIstanbulFormattedOpenedAt() throws Exception {
        PaperPositionEntity entity = new PaperPositionEntity();
        entity.setId(1L);
        entity.setSymbol("BTCUSDT");
        entity.setSide(PositionSide.LONG);
        entity.setStatus(PaperPositionStatus.OPEN);
        entity.setEntryAction(EntryAction.ENTER_LONG);
        entity.setOpenedAt(Instant.parse("2026-06-07T20:25:10Z"));

        var response = mapper.toResponse(entity);

        assertThat(response.openedAt()).isEqualTo("2026-06-07 23:25:10 TRT");
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("openedAtIstanbulText");
    }
}
