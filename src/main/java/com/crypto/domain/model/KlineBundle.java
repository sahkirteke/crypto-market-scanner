package com.crypto.domain.model;

import com.crypto.common.enums.EliminationReason;
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
public class KlineBundle {
    private String symbol;
    @Builder.Default
    private List<Kline> oneHourKlines = new ArrayList<>();
    @Builder.Default
    private List<Kline> fourHourKlines = new ArrayList<>();
    @Builder.Default
    private Boolean ready = false;
    @Builder.Default
    private EliminationReason eliminatedReason = EliminationReason.NONE;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
}
