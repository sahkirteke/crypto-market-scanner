package com.crypto.scanner.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class V20SignalResult {
    private boolean baseSignalPass;
    private boolean entryFiltersPass;
    private boolean scorePass;
    private int signalScore;
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
