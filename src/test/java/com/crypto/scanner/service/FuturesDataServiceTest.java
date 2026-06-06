package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.binance.client.BinanceClientException;
import com.crypto.binance.dto.BinanceFundingRateDto;
import com.crypto.binance.dto.BinanceOpenInterestDto;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FuturesDataServiceTest {
    private static final String SYMBOL = "BTCUSDT";

    @Test
    void loadForSymbolAddsFundingNormalWarningWhenFundingIsInNormalRange() {
        BinanceFuturesClient client = clientWithFunding("0.0001");

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getWarnings()).contains(ReasonTag.FUNDING_NORMAL);
        assertThat(snapshot.getLongCrowded()).isFalse();
        assertThat(snapshot.getShortCrowded()).isFalse();
    }

    @Test
    void loadForSymbolAddsLongCrowdedWarningWhenFundingIsDangerPositive() {
        BinanceFuturesClient client = clientWithFunding("0.0012");

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getWarnings()).contains(ReasonTag.LONG_CROWDED);
        assertThat(snapshot.getLongCrowded()).isTrue();
    }

    @Test
    void loadForSymbolAddsSlightlyHighWarningWhenFundingIsWarningPositive() {
        BinanceFuturesClient client = clientWithFunding("0.0006");

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getWarnings()).contains(ReasonTag.FUNDING_SLIGHTLY_HIGH);
        assertThat(snapshot.getLongCrowded()).isFalse();
    }

    @Test
    void loadForSymbolAddsShortCrowdedWarningWhenFundingIsDangerNegative() {
        BinanceFuturesClient client = clientWithFunding("-0.0012");

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getWarnings()).contains(ReasonTag.SHORT_CROWDED);
        assertThat(snapshot.getShortCrowded()).isTrue();
    }

    @Test
    void loadForSymbolAddsSlightlyNegativeWarningWhenFundingIsWarningNegative() {
        BinanceFuturesClient client = clientWithFunding("-0.0006");

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getWarnings()).contains(ReasonTag.FUNDING_SLIGHTLY_NEGATIVE);
        assertThat(snapshot.getShortCrowded()).isFalse();
    }

    @Test
    void loadForSymbolSelectsLatestFundingTime() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getFundingRate(SYMBOL)).thenReturn(List.of(
                funding("0.0001", 1000L),
                funding("0.0008", 2000L)
        ));

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getFundingRate()).isEqualByComparingTo(new BigDecimal("0.0008"));
    }

    @Test
    void loadForSymbolAddsOpenInterestMissingWarningWhenOpenInterestIsNull() {
        BinanceFuturesClient client = clientWithFunding("0.0001");
        when(client.getOpenInterest(SYMBOL)).thenReturn(null);

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getOpenInterest()).isNull();
        assertThat(snapshot.getWarnings()).contains(ReasonTag.OPEN_INTEREST_MISSING);
    }

    @Test
    void loadForSymbolParsesOpenInterestAsBigDecimal() {
        BinanceFuturesClient client = clientWithFunding("0.0001");
        when(client.getOpenInterest(SYMBOL)).thenReturn(new BinanceOpenInterestDto(SYMBOL, "12345.678", 2000L));

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot.getOpenInterest()).isEqualByComparingTo(new BigDecimal("12345.678"));
    }

    @Test
    void loadForSymbolDoesNotFailWhenFundingEndpointThrows() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getFundingRate(SYMBOL)).thenThrow(new BinanceClientException("funding failed"));
        when(client.getOpenInterest(SYMBOL)).thenReturn(new BinanceOpenInterestDto(SYMBOL, "12345.678", 2000L));

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSymbol()).isEqualTo(SYMBOL);
        assertThat(snapshot.getFundingRate()).isNull();
        assertThat(snapshot.getOpenInterest()).isEqualByComparingTo(new BigDecimal("12345.678"));
    }

    @Test
    void loadForSymbolDoesNotFailWhenOpenInterestEndpointThrows() {
        BinanceFuturesClient client = clientWithFunding("0.0001");
        when(client.getOpenInterest(SYMBOL)).thenThrow(new BinanceClientException("open interest failed"));

        FuturesSnapshot snapshot = service(client).loadForSymbol(SYMBOL);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSymbol()).isEqualTo(SYMBOL);
        assertThat(snapshot.getWarnings()).contains(ReasonTag.OPEN_INTEREST_MISSING);
    }

    @Test
    void loadForSymbolsReturnsEmptyMapWhenSymbolsAreNull() {
        Map<String, FuturesSnapshot> snapshots = service(mock(BinanceFuturesClient.class)).loadForSymbols(null);

        assertThat(snapshots).isEmpty();
    }

    private FuturesDataService service(BinanceFuturesClient client) {
        return new FuturesDataService(client, new ScannerProperties());
    }

    private BinanceFuturesClient clientWithFunding(String fundingRate) {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getFundingRate(SYMBOL)).thenReturn(List.of(funding(fundingRate, 1000L)));
        return client;
    }

    private BinanceFundingRateDto funding(String fundingRate, Long fundingTime) {
        return new BinanceFundingRateDto(SYMBOL, fundingRate, fundingTime);
    }
}
