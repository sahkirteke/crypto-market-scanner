package com.crypto.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.common.enums.CoinClassification;
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
        MarketScanRunEntity scanRun = new MarketScanRunEntity();
        scanRun.setId(1L);
        CoinScanResultEntity entity = new CoinScanResultEntity();
        entity.setId(10L);
        entity.setScanRun(scanRun);
        entity.setSymbol("BTCUSDT");
        entity.setClassification(CoinClassification.WATCHLIST);
        entity.setReasonsJson("[\"MARKET_CHOP\"]");
        entity.setWarningsJson("[\"VOLUME_WEAK\"]");
        entity.setCreatedAt(Instant.parse("2026-06-07T10:00:00Z"));

        CoinScanResultResponse response = mapper.toCoinResponse(entity);

        assertThat(response.scanRunId()).isEqualTo(1L);
        assertThat(response.reasons()).containsExactly("MARKET_CHOP");
        assertThat(response.warnings()).containsExactly("VOLUME_WEAK");
    }
}
