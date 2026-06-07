package com.crypto.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.api.dto.CoinScanResultResponse;
import com.crypto.api.dto.LatestScanResponse;
import com.crypto.api.dto.ScanDetailResponse;
import com.crypto.api.exception.ResourceNotFoundException;
import com.crypto.api.mapper.ScannerApiMapper;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.ScanType;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ScannerQueryServiceTest {
    private MarketScanRunRepository marketScanRunRepository;
    private CoinScanResultRepository coinScanResultRepository;
    private ScannerQueryService scannerQueryService;

    @BeforeEach
    void setUp() {
        marketScanRunRepository = mock(MarketScanRunRepository.class);
        coinScanResultRepository = mock(CoinScanResultRepository.class);
        ScannerApiMapper mapper = new ScannerApiMapper(new JsonTextMapper(new ObjectMapper()));
        scannerQueryService = new ScannerQueryService(marketScanRunRepository, coinScanResultRepository, mapper);
    }

    @Test
    void getLatestCompletedScanThrowsWhenNoCompletedScanExists() {
        when(marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scannerQueryService.getLatestCompletedScan())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Completed scanner run not found");
    }

    @Test
    void getLatestCompletedScanReturnsStrongLongStrongShortAndWatchlistLists() {
        MarketScanRunEntity scanRun = scanRun(1L);
        when(marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED"))
                .thenReturn(Optional.of(scanRun));
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.STRONG_LONG))
                .thenReturn(List.of(coin(scanRun, "BTCUSDT", CoinClassification.STRONG_LONG)));
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.STRONG_SHORT))
                .thenReturn(List.of(coin(scanRun, "ETHUSDT", CoinClassification.STRONG_SHORT)));
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.WATCHLIST))
                .thenReturn(List.of(coin(scanRun, "SOLUSDT", CoinClassification.WATCHLIST)));

        LatestScanResponse response = scannerQueryService.getLatestCompletedScan();

        assertThat(response.strongLong()).map(CoinScanResultResponse::symbol).containsExactly("BTCUSDT");
        assertThat(response.strongShort()).map(CoinScanResultResponse::symbol).containsExactly("ETHUSDT");
        assertThat(response.watchlist()).map(CoinScanResultResponse::symbol).containsExactly("SOLUSDT");
        verify(coinScanResultRepository, never())
                .findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.ELIMINATED);
    }

    @Test
    void getRecentScansCapsLimitAtMax100() {
        when(marketScanRunRepository.findAllByOrderByScanTimeUtcDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scanRun(1L))));

        scannerQueryService.getRecentScans(1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(marketScanRunRepository).findAllByOrderByScanTimeUtcDesc(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getScanDetailReturnsEmptyEliminatedWhenIncludeEliminatedIsFalse() {
        MarketScanRunEntity scanRun = scanRun(1L);
        when(marketScanRunRepository.findById(1L)).thenReturn(Optional.of(scanRun));
        stubPrimaryClassifications(scanRun);

        ScanDetailResponse response = scannerQueryService.getScanDetail(1L, false);

        assertThat(response.eliminated()).isEmpty();
        verify(coinScanResultRepository, never())
                .findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.ELIMINATED);
    }

    @Test
    void getScanDetailReturnsEliminatedWhenIncludeEliminatedIsTrue() {
        MarketScanRunEntity scanRun = scanRun(1L);
        when(marketScanRunRepository.findById(1L)).thenReturn(Optional.of(scanRun));
        stubPrimaryClassifications(scanRun);
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.ELIMINATED))
                .thenReturn(List.of(coin(scanRun, "XRPUSDT", CoinClassification.ELIMINATED)));

        ScanDetailResponse response = scannerQueryService.getScanDetail(1L, true);

        assertThat(response.eliminated()).map(CoinScanResultResponse::symbol).containsExactly("XRPUSDT");
    }

    @Test
    void getSymbolHistoryNormalizesLowercaseSymbolToUppercase() {
        when(coinScanResultRepository.findBySymbolOrderByCreatedAtDesc(eq("BTCUSDT"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        scannerQueryService.getSymbolHistory("btcusdt", 20);

        verify(coinScanResultRepository).findBySymbolOrderByCreatedAtDesc(eq("BTCUSDT"), any(Pageable.class));
    }

    private void stubPrimaryClassifications(MarketScanRunEntity scanRun) {
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.STRONG_LONG))
                .thenReturn(List.of(coin(scanRun, "BTCUSDT", CoinClassification.STRONG_LONG)));
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.STRONG_SHORT))
                .thenReturn(List.of(coin(scanRun, "ETHUSDT", CoinClassification.STRONG_SHORT)));
        when(coinScanResultRepository.findByScanRun_IdAndClassificationOrderByScoreDesc(1L, CoinClassification.WATCHLIST))
                .thenReturn(List.of(coin(scanRun, "SOLUSDT", CoinClassification.WATCHLIST)));
    }

    private MarketScanRunEntity scanRun(Long id) {
        MarketScanRunEntity entity = new MarketScanRunEntity();
        entity.setId(id);
        entity.setScanType(ScanType.FOUR_HOUR);
        entity.setScanTimeUtc(Instant.parse("2026-06-07T10:00:00Z"));
        entity.setStatus("COMPLETED");
        entity.setCreatedAt(Instant.parse("2026-06-07T10:00:00Z"));
        return entity;
    }

    private CoinScanResultEntity coin(MarketScanRunEntity scanRun, String symbol, CoinClassification classification) {
        CoinScanResultEntity entity = new CoinScanResultEntity();
        entity.setId((long) symbol.hashCode());
        entity.setScanRun(scanRun);
        entity.setSymbol(symbol);
        entity.setClassification(classification);
        entity.setScore(classification == CoinClassification.ELIMINATED ? 0 : 80);
        entity.setCreatedAt(Instant.parse("2026-06-07T10:00:00Z"));
        return entity;
    }
}
