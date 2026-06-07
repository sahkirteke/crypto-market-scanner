package com.crypto.scanner.model;

import java.math.BigDecimal;
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
public class BollingerScoreResult {
    @Builder.Default
    private BigDecimal bbScore = BigDecimal.ZERO;
    @Builder.Default
    private List<String> bbReasons = new ArrayList<>();
    private BigDecimal bbPercentB;
    private BigDecimal bbWidth;
    private BigDecimal bbUpper;
    private BigDecimal bbMiddle;
    private BigDecimal bbLower;
    private Boolean bbUpperTouched;
    private Boolean bbLowerTouched;
    private Boolean bbUpperClosedOutside;
    private Boolean bbLowerClosedOutside;
}
