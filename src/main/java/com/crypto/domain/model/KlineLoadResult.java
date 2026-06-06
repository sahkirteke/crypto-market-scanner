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
public class KlineLoadResult {
    @Builder.Default
    private List<KlineBundle> readyBundles = new ArrayList<>();
    @Builder.Default
    private List<KlineBundle> notReadyBundles = new ArrayList<>();
    @Builder.Default
    private Integer totalCount = 0;
    @Builder.Default
    private Integer readyCount = 0;
    @Builder.Default
    private Integer notReadyCount = 0;
}
