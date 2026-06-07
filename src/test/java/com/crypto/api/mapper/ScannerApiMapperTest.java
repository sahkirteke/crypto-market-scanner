package com.crypto.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScannerApiMapperTest {
    private ScannerApiMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ScannerApiMapper(new JsonTextMapper(new ObjectMapper()));
    }

    @Test
    void toCoinResponseParsesJsonReasonsAndWarnings() {
        CoinScanResultEntity entity = coin(DirectionBias.NEUTRAL, "[\"MARKET_CHOP\"]");
        entity.setWarningsJson("[\"VOLUME_WEAK\"]");

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.scanRunId()).isEqualTo(1L);
        assertThat(response.reasons()).containsExactly("MARKET_CHOP");
        assertThat(response.warnings()).containsExactly("VOLUME_WEAK");
    }

    @Test
    void toCoinResponseSelectsCommonAndShortReasonsForShortDirectionBias() {
        CoinScanResultEntity entity = coin(
                DirectionBias.SHORT,
                "[\"MARKET_CHOP\",\"MOMENTUM_1H_POSITIVE\",\"RSI_IDEAL_LONG\","
                        + "\"VOLUME_CONFIRMED\",\"TREND_4H_NEGATIVE\",\"RSI_IDEAL_SHORT\"]"
        );

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.longReasons()).containsExactly("MOMENTUM_1H_POSITIVE", "RSI_IDEAL_LONG");
        assertThat(response.shortReasons()).containsExactly("TREND_4H_NEGATIVE", "RSI_IDEAL_SHORT");
        assertThat(response.commonReasons()).containsExactly("MARKET_CHOP", "VOLUME_CONFIRMED");
        assertThat(response.selectedDirectionReasons()).containsExactly(
                "MARKET_CHOP",
                "VOLUME_CONFIRMED",
                "TREND_4H_NEGATIVE",
                "RSI_IDEAL_SHORT"
        );
        assertThat(response.selectedDirectionReasons()).doesNotContain("RSI_IDEAL_LONG");
    }

    @Test
    void toCoinResponseSelectsCommonAndLongReasonsForLongDirectionBias() {
        CoinScanResultEntity entity = coin(
                DirectionBias.LONG,
                "[\"MARKET_RISK_ON\",\"TREND_4H_POSITIVE\",\"MOMENTUM_1H_POSITIVE\","
                        + "\"TREND_4H_NEGATIVE\",\"RSI_IDEAL_SHORT\"]"
        );

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.longReasons()).containsExactly("TREND_4H_POSITIVE", "MOMENTUM_1H_POSITIVE");
        assertThat(response.shortReasons()).containsExactly("TREND_4H_NEGATIVE", "RSI_IDEAL_SHORT");
        assertThat(response.commonReasons()).containsExactly("MARKET_RISK_ON");
        assertThat(response.selectedDirectionReasons()).containsExactly(
                "MARKET_RISK_ON",
                "TREND_4H_POSITIVE",
                "MOMENTUM_1H_POSITIVE"
        );
        assertThat(response.selectedDirectionReasons()).doesNotContain("RSI_IDEAL_SHORT");
    }

    @Test
    void toCoinResponseSelectsOnlyCommonReasonsForNeutralDirectionBias() {
        CoinScanResultEntity entity = coin(
                DirectionBias.NEUTRAL,
                "[\"MARKET_CHOP\",\"TREND_4H_POSITIVE\",\"TREND_4H_NEGATIVE\",\"VOLUME_CONFIRMED\"]"
        );

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.commonReasons()).containsExactly("MARKET_CHOP", "VOLUME_CONFIRMED");
        assertThat(response.selectedDirectionReasons()).containsExactly("MARKET_CHOP", "VOLUME_CONFIRMED");
    }

    @Test
    void toCoinResponsePutsUnknownReasonsIntoCommonReasons() {
        CoinScanResultEntity entity = coin(
                DirectionBias.SHORT,
                "[\"UNKNOWN_REASON\",\"TREND_4H_NEGATIVE\"]"
        );

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.commonReasons()).containsExactly("UNKNOWN_REASON");
        assertThat(response.selectedDirectionReasons()).containsExactly("UNKNOWN_REASON", "TREND_4H_NEGATIVE");
    }

    private CoinScanResultEntity coin(DirectionBias directionBias, String reasonsJson) {
        MarketScanRunEntity scanRun = new MarketScanRunEntity();
        scanRun.setId(1L);
        CoinScanResultEntity entity = new CoinScanResultEntity();
        entity.setId(10L);
        entity.setScanRun(scanRun);
        entity.setSymbol("BTCUSDT");
        entity.setDirectionBias(directionBias);
        entity.setClassification(CoinClassification.WATCHLIST);
        entity.setReasonsJson(reasonsJson);
        entity.setCreatedAt(Instant.parse("2026-06-07T10:00:00Z"));
        return entity;
    }
}
