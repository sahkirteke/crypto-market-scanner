package com.crypto.persistence.mapper;

import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketScanPersistenceMapper {
    private final JsonTextMapper jsonTextMapper;

    public MarketScanRunEntity toRunEntity(MarketScanResult result, String status) {
        MarketScanRunEntity entity = new MarketScanRunEntity();
        entity.setScanTimeUtc(result.getScanTimeUtc());
        entity.setScanTimeIstanbulText(result.getScanTimeIstanbulText());
        entity.setScanType(result.getScanType());
        entity.setMarketRegime(result.getMarketRegime());
        entity.setMarketBreadthPct(result.getMarketBreadthPct());
        entity.setTotalSymbols(result.getTotalSymbols());
        entity.setPreFilterPassedCount(result.getPreFilterPassedCount());
        entity.setStrongLongCount(result.getStrongLongCount());
        entity.setStrongShortCount(result.getStrongShortCount());
        entity.setWatchlistCount(result.getWatchlistCount());
        entity.setEliminatedCount(result.getEliminatedCount());
        entity.setStatus(status);
        entity.setReasonsJson(jsonTextMapper.toJson(result.getReasons()));
        entity.setWarningsJson(jsonTextMapper.toJson(result.getWarnings()));
        return entity;
    }

    public CoinScanResultEntity toCoinEntity(CoinScanResult result, MarketScanRunEntity runEntity) {
        CoinScanResultEntity entity = new CoinScanResultEntity();
        entity.setScanRun(runEntity);
        entity.setSymbol(result.getSymbol());
        entity.setDirectionBias(result.getDirectionBias());
        entity.setClassification(result.getClassification());
        entity.setScore(result.getScore());
        entity.setLongScore(result.getLongScore());
        entity.setShortScore(result.getShortScore());
        entity.setRiskLevel(result.getRiskLevel());
        entity.setLastPrice(result.getLastPrice());
        entity.setPriceChange24hPct(result.getPriceChange24hPct());
        entity.setQuoteVolume24h(result.getQuoteVolume24h());
        entity.setSpreadPct(result.getSpreadPct());
        entity.setFundingRate(result.getFundingRate());
        entity.setOpenInterest(result.getOpenInterest());
        entity.setMarketBreadthPct(result.getMarketBreadthPct());
        entity.setReasonsJson(jsonTextMapper.toJson(result.getReasons()));
        entity.setWarningsJson(jsonTextMapper.toJson(result.getWarnings()));
        entity.setEliminatedReason(result.getEliminatedReason());
        return entity;
    }
}
