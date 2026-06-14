package com.crypto.persistence.mapper;

import com.crypto.common.time.IstanbulTimeUtil;
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
        entity.setScanTimeText(result.getScanTimeText() == null ? IstanbulTimeUtil.format(result.getScanTimeUtc()) : result.getScanTimeText());
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
        entity.setClose1h(result.getClose1h());
        entity.setEma20_1h(result.getEma20_1h());
        entity.setRsi14_1h(result.getRsi14_1h());
        entity.setVolumeRatio_1h(result.getVolumeRatio_1h());
        entity.setClose4h(result.getClose4h());
        entity.setEma20_4h(result.getEma20_4h());
        entity.setEma50_4h(result.getEma50_4h());
        entity.setEma200_4h(result.getEma200_4h());
        entity.setRsi14_4h(result.getRsi14_4h());
        entity.setMacdHist_4h(result.getMacdHist_4h());
        entity.setAtr14_4h(result.getAtr14_4h());
        entity.setVolumeRatio_4h(result.getVolumeRatio_4h());
        entity.setReasonsJson(jsonTextMapper.toJson(result.getReasons()));
        entity.setWarningsJson(jsonTextMapper.toJson(result.getWarnings()));
        entity.setEliminatedReason(result.getEliminatedReason());
        return entity;
    }
}
