package com.crypto.api.mapper;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.api.dto.MarketScanRunResponse;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScannerApiMapper {
    private static final Set<String> LONG_REASON_TAGS = Set.of(
            "TREND_4H_POSITIVE",
            "MOMENTUM_1H_POSITIVE",
            "RSI_IDEAL_LONG",
            "LATE_LONG_RISK",
            "RSI_OVERBOUGHT",
            "LONG_CROWDED",
            "PUMPED_TOO_MUCH_WARNING"
    );
    private static final Set<String> SHORT_REASON_TAGS = Set.of(
            "TREND_4H_NEGATIVE",
            "MOMENTUM_1H_NEGATIVE",
            "RSI_IDEAL_SHORT",
            "LATE_SHORT_WARNING",
            "SHORT_OVERSOLD_WARNING",
            "SHORT_EXTREME_OVERSOLD_RISK",
            "SHORT_EXTREME_LATE_DUMP_RISK",
            "SHORT_CROWDED",
            "DUMPED_TOO_MUCH_WARNING"
    );
    private static final Set<String> COMMON_REASON_TAGS = Set.of(
            "MARKET_CHOP",
            "MARKET_RISK_ON",
            "MARKET_RISK_OFF",
            "MARKET_PANIC",
            "MARKET_BREADTH_OK",
            "MARKET_BREADTH_STRONG",
            "WEAK_RISK_ON_BREADTH_FAIL",
            "VOLUME_CONFIRMED",
            "VOLUME_WEAK",
            "FUNDING_NORMAL",
            "FUNDING_SLIGHTLY_HIGH",
            "FUNDING_SLIGHTLY_NEGATIVE",
            "DATA_NOT_READY",
            "SCORE_BELOW_THRESHOLD",
            "OPEN_INTEREST_MISSING"
    );

    private final JsonTextMapper jsonTextMapper;

    public MarketScanRunResponse toRunResponse(MarketScanRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MarketScanRunResponse(
                entity.getId(),
                IstanbulTimeUtil.format(entity.getScanTimeUtc()),
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
                IstanbulTimeUtil.format(entity.getCreatedAt())
        );
    }

    public CoinScanResultResponse toCoinResponse(CoinScanResultEntity entity) {
        if (entity == null) {
            return null;
        }
        List<String> reasons = jsonTextMapper.toStringList(entity.getReasonsJson());
        List<String> longReasons = filterReasons(reasons, this::isLongReason);
        List<String> shortReasons = filterReasons(reasons, this::isShortReason);
        List<String> commonReasons = filterReasons(reasons, this::isCommonReason);
        List<String> selectedDirectionReasons = selectedDirectionReasons(
                entity.getDirectionBias(),
                commonReasons,
                longReasons,
                shortReasons
        );

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
                reasons,
                longReasons,
                shortReasons,
                commonReasons,
                selectedDirectionReasons,
                jsonTextMapper.toStringList(entity.getWarningsJson()),
                enumName(entity.getEliminatedReason()),
                IstanbulTimeUtil.format(entity.getCreatedAt())
        );
    }

    public List<CoinScanResultResponse> toCoinResponseList(List<CoinScanResultEntity> entities) {
        return entities.stream()
                .map(this::toCoinResponse)
                .toList();
    }

    private List<String> filterReasons(List<String> reasons, ReasonPredicate predicate) {
        return reasons.stream()
                .filter(predicate::matches)
                .distinct()
                .toList();
    }

    private List<String> selectedDirectionReasons(
            DirectionBias directionBias,
            List<String> commonReasons,
            List<String> longReasons,
            List<String> shortReasons
    ) {
        LinkedHashSet<String> selectedReasons = new LinkedHashSet<>(commonReasons);
        if (directionBias == DirectionBias.LONG) {
            selectedReasons.addAll(longReasons);
        } else if (directionBias == DirectionBias.SHORT) {
            selectedReasons.addAll(shortReasons);
        }
        return List.copyOf(selectedReasons);
    }

    private boolean isLongReason(String reason) {
        return reason != null && LONG_REASON_TAGS.contains(reason);
    }

    private boolean isShortReason(String reason) {
        return reason != null && SHORT_REASON_TAGS.contains(reason);
    }

    private boolean isCommonReason(String reason) {
        return reason == null
                || COMMON_REASON_TAGS.contains(reason)
                || (!isLongReason(reason) && !isShortReason(reason));
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @FunctionalInterface
    private interface ReasonPredicate {
        boolean matches(String reason);
    }
}
