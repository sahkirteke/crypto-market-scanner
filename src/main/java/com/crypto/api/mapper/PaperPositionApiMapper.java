package com.crypto.api.mapper;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
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
                entity.getOpenedAt(),
                entity.getClosedAt(),
                entity.getExitPrice(),
                entity.getRealizedPnlUsdt(),
                entity.getRealizedPnlPct(),
                entity.getExitReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public List<PaperPositionResponse> toResponseList(List<PaperPositionEntity> entities) {
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }
}
