package com.crypto.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketScanPersistenceMapperTest {
    private MarketScanPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MarketScanPersistenceMapper(new JsonTextMapper(new ObjectMapper()));
    }

    @Test
    void toRunEntityMapsMarketScanResultFields() {
        MarketScanResult result = MarketScanResult.builder()
                .scanType(ScanType.FOUR_HOUR)
                .scanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"))
                .scanTimeIstanbulText("2026-06-07 13:00:00 TRT")
                .marketRegime(MarketRegime.RISK_ON)
                .marketBreadthPct(new BigDecimal("42.12345678"))
                .totalSymbols(100)
                .preFilterPassedCount(80)
                .strongLongCount(2)
                .strongShortCount(3)
                .watchlistCount(4)
                .eliminatedCount(91)
                .reasons(List.of(ReasonTag.MARKET_RISK_ON))
                .warnings(List.of(ReasonTag.VOLUME_WEAK))
                .build();

        MarketScanRunEntity entity = mapper.toRunEntity(result, "COMPLETED");

        assertThat(entity.getScanType()).isEqualTo(ScanType.FOUR_HOUR);
        assertThat(entity.getMarketRegime()).isEqualTo(MarketRegime.RISK_ON);
        assertThat(entity.getTotalSymbols()).isEqualTo(100);
        assertThat(entity.getPreFilterPassedCount()).isEqualTo(80);
        assertThat(entity.getStrongLongCount()).isEqualTo(2);
        assertThat(entity.getStrongShortCount()).isEqualTo(3);
        assertThat(entity.getWatchlistCount()).isEqualTo(4);
        assertThat(entity.getEliminatedCount()).isEqualTo(91);
        assertThat(entity.getReasonsJson()).contains("MARKET_RISK_ON");
        assertThat(entity.getWarningsJson()).contains("VOLUME_WEAK");
    }

    @Test
    void toCoinEntityMapsCoinScanResultFields() {
        MarketScanRunEntity runEntity = new MarketScanRunEntity();
        CoinScanResult result = CoinScanResult.builder()
                .symbol("BTCUSDT")
                .directionBias(DirectionBias.LONG)
                .classification(CoinClassification.STRONG_LONG)
                .score(92)
                .longScore(92)
                .shortScore(12)
                .riskLevel(RiskLevel.LOW)
                .lastPrice(new BigDecimal("68000.123456789012"))
                .priceChange24hPct(new BigDecimal("3.25"))
                .quoteVolume24h(new BigDecimal("1000000000"))
                .spreadPct(new BigDecimal("0.01"))
                .fundingRate(new BigDecimal("0.0001000000"))
                .openInterest(new BigDecimal("500000000"))
                .marketBreadthPct(new BigDecimal("55.5"))
                .eliminatedReason(EliminationReason.NONE)
                .reasons(List.of(ReasonTag.TREND_4H_POSITIVE))
                .warnings(List.of(ReasonTag.FUNDING_SLIGHTLY_HIGH))
                .build();

        CoinScanResultEntity entity = mapper.toCoinEntity(result, runEntity);

        assertThat(entity.getScanRun()).isSameAs(runEntity);
        assertThat(entity.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(entity.getClassification()).isEqualTo(CoinClassification.STRONG_LONG);
        assertThat(entity.getScore()).isEqualTo(92);
        assertThat(entity.getEliminatedReason()).isEqualTo(EliminationReason.NONE);
        assertThat(entity.getReasonsJson()).contains("TREND_4H_POSITIVE");
        assertThat(entity.getWarningsJson()).contains("FUNDING_SLIGHTLY_HIGH");
    }
}
