package com.crypto.persistence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.mapper.MarketScanPersistenceMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketScanPersistenceServiceTest {
    private MarketScanRunRepository marketScanRunRepository;
    private CoinScanResultRepository coinScanResultRepository;
    private MarketScanPersistenceService service;

    @BeforeEach
    void setUp() {
        marketScanRunRepository = mock(MarketScanRunRepository.class);
        coinScanResultRepository = mock(CoinScanResultRepository.class);
        MarketScanPersistenceMapper mapper = new MarketScanPersistenceMapper(new JsonTextMapper(new ObjectMapper()));
        service = new MarketScanPersistenceService(marketScanRunRepository, coinScanResultRepository, mapper);
    }

    @Test
    void saveCompletedScanSkipsMarketScanAndCoinResultDbWrites() {
        MarketScanResult result = MarketScanResult.builder()
                .scanType(ScanType.FOUR_HOUR)
                .scanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"))
                .marketRegime(MarketRegime.CHOP)
                .strongLong(List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG)))
                .strongShort(List.of(coin("ETHUSDT", CoinClassification.STRONG_SHORT)))
                .watchlist(List.of(coin("SOLUSDT", CoinClassification.WATCHLIST)))
                .eliminated(List.of(coin("XRPUSDT", CoinClassification.ELIMINATED)))
                .reasons(List.of(ReasonTag.MARKET_CHOP))
                .build();

        MarketScanRunEntity saved = service.saveCompletedScan(result);

        verify(marketScanRunRepository, never()).save(any(MarketScanRunEntity.class));
        verify(coinScanResultRepository, never()).saveAll(anyList());
        assertThat(saved.getStatus()).isEqualTo(MarketScanPersistenceService.STATUS_COMPLETED);
        assertThat(saved.getId()).isNegative();
        assertThat(result.getScanRunId()).isEqualTo(saved.getId());
    }

    @Test
    void saveFailedScanSkipsMarketScanDbWrite() {
        Instant scanTime = Instant.parse("2026-06-07T10:00:00Z");

        MarketScanRunEntity saved = service.saveFailedScan(ScanType.ONE_HOUR, scanTime, "boom");

        verify(marketScanRunRepository, never()).save(any(MarketScanRunEntity.class));
        verify(coinScanResultRepository, never()).saveAll(anyList());
        assertThat(saved.getStatus()).isEqualTo(MarketScanPersistenceService.STATUS_FAILED);
        assertThat(saved.getScanType()).isEqualTo(ScanType.ONE_HOUR);
        assertThat(saved.getId()).isNegative();
    }

    private CoinScanResult coin(String symbol, CoinClassification classification) {
        return CoinScanResult.builder()
                .symbol(symbol)
                .classification(classification)
                .directionBias(classification == CoinClassification.STRONG_SHORT ? DirectionBias.SHORT : DirectionBias.LONG)
                .score(80)
                .longScore(80)
                .shortScore(80)
                .lastPrice(new java.math.BigDecimal("100"))
                .build();
    }
}
