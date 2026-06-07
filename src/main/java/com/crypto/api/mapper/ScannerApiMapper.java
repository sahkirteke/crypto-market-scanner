package com.crypto.api.mapper;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.api.dto.MarketScanRunResponse;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScannerApiMapper {
    private final JsonTextMapper jsonTextMapper;

    public MarketScanRunResponse toRunResponse(MarketScanRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MarketScanRunResponse(
                entity.getId(),
                entity.getScanTimeUtc(),
                entity.getScanTimeIstanbulText(),
                enumName(entity.getScanType()),
                enumName(entity.getMarketRegime()),
                entity.getMarketBreadthPct(),
                entity.getTotalSymbols(),
                entity.getPreFilterPassedCount(),
                entity.getStrongLongCount(),
                entity.getStrongShortCount(),
                entity.getWatchlistCount(),
                entity.getEliminatedCount(),
                entity.getStatus(),
                entity.getErrorMessage(),
                jsonTextMapper.toStringList(entity.getReasonsJson()),
                jsonTextMapper.toStringList(entity.getWarningsJson()),
                entity.getCreatedAt()
        );
    }

    public CoinScanResultResponse toCoinResponse(CoinScanResultEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CoinScanResultResponse(
                entity.getId(),
                entity.getScanRun() == null ? null : entity.getScanRun().getId(),
                entity.getSymbol(),
                enumName(entity.getDirectionBias()),
                enumName(entity.getClassification()),
                entity.getScore(),
                entity.getLongScore(),
                entity.getShortScore(),
                enumName(entity.getRiskLevel()),
                entity.getLastPrice(),
                entity.getPriceChange24hPct(),
                entity.getQuoteVolume24h(),
                entity.getSpreadPct(),
                entity.getFundingRate(),
                entity.getOpenInterest(),
                entity.getMarketBreadthPct(),
                jsonTextMapper.toStringList(entity.getReasonsJson()),
                jsonTextMapper.toStringList(entity.getWarningsJson()),
                enumName(entity.getEliminatedReason()),
                entity.getCreatedAt()
        );
    }

    public List<CoinScanResultResponse> toCoinResponseList(List<CoinScanResultEntity> entities) {
        return entities.stream()
                .map(this::toCoinResponse)
                .toList();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
