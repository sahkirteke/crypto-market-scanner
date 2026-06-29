package com.crypto.persistence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.mockito.ArgumentCaptor;

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
    void saveCompletedScanCombinesAllResultListsAndSavesCoins() {
        when(marketScanRunRepository.save(any(MarketScanRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MarketScanResult result = MarketScanResult.builder()
                .scanType(ScanType.FOUR_HOUR)
                .scanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"))
                .marketRegime(MarketRegime.CHOP)
                .strongLong(List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG)))
                .strongShort(List.of(coin("ETHUSDT", CoinClassification.STRONG_SHORT)))
                .watchlist(List.of(coin("SOLUSDT", CoinClassification.WATCHLIST)))
                .eliminated(List.of(
                        coin("XRPUSDT", CoinClassification.ELIMINATED),
                        coin("ADAUSDT", CoinClassification.ELIMINATED)))
                .reasons(List.of(ReasonTag.MARKET_CHOP))
                .build();

        service.saveCompletedScan(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CoinScanResultEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(coinScanResultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(5);
        assertThat(captor.getValue()).extracting(CoinScanResultEntity::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT");
    }

    @Test
    void saveCompletedScanPersistsCoinResultsInChunks() {
        when(marketScanRunRepository.save(any(MarketScanRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MarketScanResult result = MarketScanResult.builder()
                .scanType(ScanType.ONE_HOUR)
                .scanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"))
                .eliminated(java.util.stream.IntStream.range(0, 205)
                        .mapToObj(index -> coin("SYM" + index, CoinClassification.ELIMINATED))
                        .toList())
                .build();

        service.saveCompletedScan(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CoinScanResultEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(coinScanResultRepository, times(3)).saveAll(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 100, 5);
    }

    @Test
    void saveCompletedScanRetriesTransientCoinChunkFailure() {
        when(marketScanRunRepository.save(any(MarketScanRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataAccessResourceFailureException("connection reset"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(coinScanResultRepository).saveAll(anyList());
        MarketScanResult result = MarketScanResult.builder()
                .scanType(ScanType.ONE_HOUR)
                .scanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"))
                .strongLong(List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG)))
                .build();

        service.saveCompletedScan(result);

        verify(coinScanResultRepository, times(2)).saveAll(anyList());
    }

    @Test
    void saveFailedScanSavesRunWithFailedStatus() {
        when(marketScanRunRepository.save(any(MarketScanRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant scanTime = Instant.parse("2026-06-07T10:00:00Z");

        MarketScanRunEntity saved = service.saveFailedScan(ScanType.ONE_HOUR, scanTime, "boom");

        ArgumentCaptor<MarketScanRunEntity> captor = ArgumentCaptor.forClass(MarketScanRunEntity.class);
        verify(marketScanRunRepository).save(captor.capture());
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getScanType()).isEqualTo(ScanType.ONE_HOUR);
        assertThat(captor.getValue().getScanTimeUtc()).isEqualTo(scanTime);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("boom");
        assertThat(captor.getValue().getTotalSymbols()).isZero();
    }

    private CoinScanResult coin(String symbol, CoinClassification classification) {
        return CoinScanResult.builder()
                .symbol(symbol)
                .classification(classification)
                .directionBias(DirectionBias.NEUTRAL)
                .score(classification == CoinClassification.ELIMINATED ? 0 : 70)
                .eliminatedReason(classification == CoinClassification.ELIMINATED
                        ? EliminationReason.SCORE_BELOW_THRESHOLD
                        : EliminationReason.NONE)
                .reasons(List.of(ReasonTag.DATA_NOT_READY))
                .warnings(List.of())
                .build();
    }
}
