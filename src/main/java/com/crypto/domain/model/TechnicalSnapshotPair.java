package com.crypto.domain.model;

import com.crypto.common.enums.ReasonTag;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalSnapshotPair {
    private String symbol;
    private TechnicalSnapshot oneHour;
    private TechnicalSnapshot fourHour;
    @Builder.Default
    private Boolean ready = false;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
}
