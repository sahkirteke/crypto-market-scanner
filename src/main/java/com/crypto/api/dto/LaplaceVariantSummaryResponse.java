package com.crypto.api.dto;
import com.crypto.laplace.model.LaplacePaperVariant;import java.math.BigDecimal;
public record LaplaceVariantSummaryResponse(LaplacePaperVariant paperVariant,int openPositionCount,int closedPositionCount,BigDecimal closedNetPnlUsdt){}
