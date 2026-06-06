package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.scanner.config.ScannerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class SymbolUniverseServiceTest {

    @Test
    void loadTradableSymbolsReturnsOnlyUsdtPerpetualTradingSymbols() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getExchangeInfo()).thenReturn(List.of(
                symbol("BTCUSDT", "USDT", "PERPETUAL", "TRADING"),
                symbol("ETHUSDT", "USDT", "PERPETUAL", "TRADING"),
                symbol("BTCUSD", "USD", "PERPETUAL", "TRADING"),
                symbol("XRPUSDT", "USDT", "CURRENT_QUARTER", "TRADING"),
                symbol("ADAUSDT", "USDT", "PERPETUAL", "BREAK")
        ));
        SymbolUniverseService service = new SymbolUniverseService(client, properties(List.of()));

        List<SymbolInfo> result = service.loadTradableSymbols();

        assertThat(result)
                .extracting(SymbolInfo::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void loadTradableSymbolsAppliesBlacklistCaseInsensitively() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getExchangeInfo()).thenReturn(List.of(
                symbol("CRVUSDT", "USDT", "PERPETUAL", "TRADING")
        ));
        SymbolUniverseService service = new SymbolUniverseService(client, properties(List.of("crvusdt")));

        List<SymbolInfo> result = service.loadTradableSymbols();

        assertThat(result).isEmpty();
    }

    @Test
    void loadTradableSymbolsDoesNotExcludeCrvusdtWhenBlacklistIsEmpty() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getExchangeInfo()).thenReturn(List.of(
                symbol("CRVUSDT", "USDT", "PERPETUAL", "TRADING")
        ));
        SymbolUniverseService service = new SymbolUniverseService(client, properties(List.of()));

        List<SymbolInfo> result = service.loadTradableSymbols();

        assertThat(result)
                .extracting(SymbolInfo::getSymbol)
                .containsExactly("CRVUSDT");
    }

    @Test
    void loadTradableSymbolsSkipsRecordsWithNullRequiredFieldsWithoutThrowing() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getExchangeInfo()).thenReturn(List.of(
                symbol(null, "USDT", "PERPETUAL", "TRADING"),
                symbol("NOQUOTE", null, "PERPETUAL", "TRADING"),
                symbol("NOCONTRACT", "USDT", null, "TRADING"),
                symbol("NOSTATUS", "USDT", "PERPETUAL", null),
                symbol("BTCUSDT", "USDT", "PERPETUAL", "TRADING")
        ));
        SymbolUniverseService service = new SymbolUniverseService(client, properties(List.of()));

        assertThatCode(service::loadTradableSymbols).doesNotThrowAnyException();
        assertThat(service.loadTradableSymbols())
                .extracting(SymbolInfo::getSymbol)
                .containsExactly("BTCUSDT");
    }

    private ScannerProperties properties(List<String> blacklist) {
        ScannerProperties properties = new ScannerProperties();
        properties.setBlacklist(blacklist);
        return properties;
    }

    private SymbolInfo symbol(String symbol, String quoteAsset, String contractType, String status) {
        return SymbolInfo.builder()
                .symbol(symbol)
                .quoteAsset(quoteAsset)
                .contractType(contractType)
                .status(status)
                .build();
    }
}
