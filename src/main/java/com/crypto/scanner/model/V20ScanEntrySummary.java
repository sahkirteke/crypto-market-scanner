package com.crypto.scanner.model;

import com.crypto.persistence.entity.PaperPositionEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class V20ScanEntrySummary {
    private int scannedSymbolCount;
    private int longCandidateCount;
    private int shortCandidateCount;
    private int openedLongCount;
    private int openedShortCount;
    private int skippedOpenPositionCount;
    private int skippedInvalidQuantityCount;
    private int skippedDataNotReadyCount;
    private int skippedPanicCount;
    @Builder.Default
    private List<PaperPositionEntity> openedPositions = new ArrayList<>();
}
