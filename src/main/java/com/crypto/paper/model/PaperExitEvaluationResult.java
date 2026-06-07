package com.crypto.paper.model;

import com.crypto.persistence.entity.PaperPositionEntity;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaperExitEvaluationResult {
    private final int checkedCount;
    private final int eventCount;
    private final int closedCount;
    private final List<PaperPositionEntity> closedPositions;
}
