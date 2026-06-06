package com.crypto.domain.model;

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
public class PreFilterResult {
    @Builder.Default
    private List<SymbolInfo> passedSymbols = new ArrayList<>();
    @Builder.Default
    private List<FilterDecision> decisions = new ArrayList<>();
    @Builder.Default
    private List<FilterDecision> eliminatedDecisions = new ArrayList<>();
    @Builder.Default
    private Integer totalCount = 0;
    @Builder.Default
    private Integer passedCount = 0;
    @Builder.Default
    private Integer eliminatedCount = 0;
}
