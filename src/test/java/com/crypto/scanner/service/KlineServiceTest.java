package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.scanner.config.ScannerProperties;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KlineServiceTest {

    @Test
    void removeOpenCandlesRemovesUnclosedCandles() {
        KlineService service = service(mock(BinanceFuturesClient.class));
        List<Kline> input = List.of(
                closedKline(Instant.now().minusSeconds(240)),
                closedKline(Instant.now().minusSeconds(180)),
                Kline.builder()
                        .closeTime(Instant.now().plusSeconds(60))
                        .closed(true)
                        .build(),
                Kline.builder()
                        .closeTime(Instant.now().minusSeconds(60))
                        .closed(false)
                        .build()
        );

        List<Kline> result = service.removeOpenCandles(input);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Kline::getClosed).containsOnly(true);
    }

    @Test
    void loadForSymbolReturnsReadyWhenOneHourAndFourHourHaveEnoughClosedCandles() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "1h", 250)).thenReturn(closedKlines(220));
        when(client.getKlines("BTCUSDT", "4h", 250)).thenReturn(closedKlines(220));
        KlineService service = service(client);

        KlineBundle result = service.loadForSymbol("BTCUSDT");

        assertThat(result.getReady()).isTrue();
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.NONE);
        assertThat(result.getOneHourKlines()).hasSize(220);
        assertThat(result.getFourHourKlines()).hasSize(220);
    }

    @Test
    void loadForSymbolReturnsDataNotReadyWhenOneHourClosedCandlesAreInsufficient() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "1h", 250)).thenReturn(closedKlines(100));
        when(client.getKlines("BTCUSDT", "4h", 250)).thenReturn(closedKlines(220));
        KlineService service = service(client);

        KlineBundle result = service.loadForSymbol("BTCUSDT");

        assertThat(result.getReady()).isFalse();
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.DATA_NOT_READY);
        assertThat(result.getReasons()).contains(ReasonTag.DATA_NOT_READY);
    }

    @Test
    void loadForSymbolReturnsDataNotReadyWhenFourHourClosedCandlesAreInsufficient() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "1h", 250)).thenReturn(closedKlines(220));
        when(client.getKlines("BTCUSDT", "4h", 250)).thenReturn(closedKlines(100));
        KlineService service = service(client);

        KlineBundle result = service.loadForSymbol("BTCUSDT");

        assertThat(result.getReady()).isFalse();
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.DATA_NOT_READY);
        assertThat(result.getReasons()).contains(ReasonTag.DATA_NOT_READY);
    }

    @Test
    void loadForSymbolReturnsDataErrorWhenBinanceClientThrowsException() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "1h", 250)).thenThrow(new RuntimeException("boom"));
        KlineService service = service(client);

        KlineBundle result = service.loadForSymbol("BTCUSDT");

        assertThat(result.getReady()).isFalse();
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.DATA_ERROR);
        assertThat(result.getReasons()).contains(ReasonTag.DATA_NOT_READY);
    }

    @Test
    void loadForSymbolsReturnsEmptyResultWhenInputIsNull() {
        KlineService service = service(mock(BinanceFuturesClient.class));

        KlineLoadResult result = service.loadForSymbols(null);

        assertThat(result.getReadyBundles()).isEmpty();
        assertThat(result.getNotReadyBundles()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getReadyCount()).isZero();
        assertThat(result.getNotReadyCount()).isZero();
    }

    @Test
    void loadForSymbolsSplitsReadyAndNotReadyBundles() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "1h", 250)).thenReturn(closedKlines(220));
        when(client.getKlines("BTCUSDT", "4h", 250)).thenReturn(closedKlines(220));
        when(client.getKlines("ETHUSDT", "1h", 250)).thenReturn(closedKlines(100));
        when(client.getKlines("ETHUSDT", "4h", 250)).thenReturn(closedKlines(220));
        KlineService service = service(client);

        KlineLoadResult result = service.loadForSymbols(List.of(symbol("BTCUSDT"), symbol("ETHUSDT")));

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getReadyCount()).isOne();
        assertThat(result.getNotReadyCount()).isOne();
        assertThat(result.getReadyBundles()).extracting(KlineBundle::getSymbol).containsExactly("BTCUSDT");
        assertThat(result.getNotReadyBundles()).extracting(KlineBundle::getSymbol).containsExactly("ETHUSDT");
    }

    private KlineService service(BinanceFuturesClient client) {
        ScannerProperties properties = new ScannerProperties();
        ScannerProperties.Klines klines = new ScannerProperties.Klines();
        klines.setOneHourLimit(250);
        klines.setFourHourLimit(250);
        klines.setMinClosedCandles(220);
        properties.setKlines(klines);
        return new KlineService(client, properties);
    }

    private List<Kline> closedKlines(int count) {
        Instant start = Instant.now().minusSeconds(count * 3600L + 3600L);
        return IntStream.range(0, count)
                .mapToObj(index -> Kline.builder()
                        .openTime(start.plusSeconds(index * 3600L))
                        .closeTime(start.plusSeconds(index * 3600L + 3599L))
                        .closed(true)
                        .build())
                .toList();
    }

    private Kline closedKline(Instant closeTime) {
        return Kline.builder()
                .closeTime(closeTime)
                .closed(true)
                .build();
    }

    private SymbolInfo symbol(String symbol) {
        return SymbolInfo.builder()
                .symbol(symbol)
                .build();
    }
}
