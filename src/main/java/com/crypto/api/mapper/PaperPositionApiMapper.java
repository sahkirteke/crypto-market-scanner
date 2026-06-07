package com.crypto.api.mapper;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.common.time.IstanbulTimeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaperPositionApiMapper {
    private final JsonTextMapper jsonTextMapper;

    public PaperPositionResponse toResponse(PaperPositionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaperPositionResponse(
                entity.getId(),
                entity.getSymbol(),
                entity.getSide(),
                entity.getStatus(),
                entity.getEntryAction(),
                entity.getEntryPrice(),
                entity.getBidPrice(),
                entity.getAskPrice(),
                entity.getMidPrice(),
                entity.getEntryPriceAdjusted(),
                entity.getExitPriceAdjusted(),
                entity.getInitialStop(),
                entity.getCurrentStop(),
                entity.getRiskPerUnit(),
                entity.getTp1(),
                entity.getTp2(),
                entity.getTp1Hit(),
                entity.getTp2Hit(),
                entity.getTrailingActive(),
                IstanbulTimeUtil.format(entity.getTrailingActivatedAtBarCloseTime()),
                entity.getRemainingPositionPct(),
                entity.getHighestPriceSinceEntry(),
                entity.getLowestPriceSinceEntry(),
                entity.getRawRealizedPnlPct(),
                entity.getNetRealizedPnlPct(),
                entity.getLeveragedNetRealizedPnlPct(),
                entity.getRawUnrealizedPnlPct(),
                entity.getNetUnrealizedPnlPct(),
                entity.getLeveragedNetUnrealizedPnlPct(),
                entity.getTotalFeePct(),
                entity.getTotalSlippagePct(),
                entity.getBarsInPosition(),
                entity.getQuantity(),
                entity.getNotionalUsdt(),
                entity.getLeverage(),
                entity.getEntryScore(),
                entity.getLongScore(),
                entity.getShortScore(),
                entity.getSourceClassification(),
                entity.getDirectionBias(),
                entity.getRiskLevel(),
                entity.getFundingRate(),
                entity.getOpenInterest(),
                entity.getMarketBreadthPct(),
                entity.getPriceChange24hPct(),
                entity.getSpreadPct(),
                entity.getEntryReason(),
                entity.getSignalReason(),
                jsonTextMapper.toStringList(entity.getReasonsJson()),
                jsonTextMapper.toStringList(entity.getWarningsJson()),
                IstanbulTimeUtil.format(entity.getOpenedAt()),
                entity.getCurrentPrice(),
                entity.getHighestPrice(),
                entity.getLowestPrice(),
                entity.getMaxFavorableMovePct(),
                entity.getMaxAdverseMovePct(),
                entity.getBarsHeld(),
                entity.getMinutesHeld(),
                entity.getEntrySignalScore(),
                IstanbulTimeUtil.format(entity.getLastCheckedAt()),
                entity.getTakeProfitPct(),
                entity.getStopLossPct(),
                entity.getTimeStopMinutes(),
                IstanbulTimeUtil.format(entity.getClosedAt()),
                entity.getExitPrice(),
                entity.getRealizedPnlUsdt(),
                entity.getRealizedPnlPct(),
                entity.getExitReason(),
                entity.getExitDetail(),
                IstanbulTimeUtil.format(entity.getCreatedAt()),
                IstanbulTimeUtil.format(entity.getUpdatedAt())
        );
    }

    public List<PaperPositionResponse> toResponseList(List<PaperPositionEntity> entities) {
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }
}
