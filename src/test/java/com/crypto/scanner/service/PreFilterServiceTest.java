package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FilterDecision;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreFilterServiceTest {

    @Test
    void applyEliminatesWhenTickerIsMissing() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(),
                List.of(bookTicker("BTCUSDT", "0.01"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isZero();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.MISSING_TICKER);
        assertThat(decision.getReasons()).contains(ReasonTag.MISSING_TICKER);
    }

    @Test
    void applyEliminatesWhenBookTickerIsMissing() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "50000000", "0")),
                List.of()
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isZero();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.MISSING_BOOK_TICKER);
        assertThat(decision.getReasons()).contains(ReasonTag.MISSING_BOOK_TICKER);
    }

    @Test
    void applyEliminatesWhenQuoteVolumeIsLow() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "1000000", "0")),
                List.of(bookTicker("BTCUSDT", "0.01"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isZero();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.LOW_VOLUME);
        assertThat(decision.getReasons()).contains(ReasonTag.LOW_VOLUME);
    }

    @Test
    void applyEliminatesWhenSpreadIsHigh() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "50000000", "0")),
                List.of(bookTicker("BTCUSDT", "0.20"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isZero();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.HIGH_SPREAD);
        assertThat(decision.getReasons()).contains(ReasonTag.HIGH_SPREAD);
    }

    @Test
    void applyAddsPumpWarningWithoutEliminating() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "50000000", "30")),
                List.of(bookTicker("BTCUSDT", "0.01"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isOne();
        assertThat(decision.getPassed()).isTrue();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.NONE);
        assertThat(decision.getWarnings()).contains(ReasonTag.PUMPED_TOO_MUCH_WARNING);
    }

    @Test
    void applyAddsDumpWarningWithoutEliminating() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "50000000", "-30")),
                List.of(bookTicker("BTCUSDT", "0.01"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isOne();
        assertThat(decision.getPassed()).isTrue();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.NONE);
        assertThat(decision.getWarnings()).contains(ReasonTag.DUMPED_TOO_MUCH_WARNING);
    }

    @Test
    void applyPassesValidCoin() {
        PreFilterResult result = service().apply(
                List.of(symbol("BTCUSDT")),
                List.of(ticker("BTCUSDT", "50000000", "5")),
                List.of(bookTicker("BTCUSDT", "0.01"))
        );

        FilterDecision decision = result.getDecisions().get(0);
        assertThat(result.getPassedCount()).isOne();
        assertThat(result.getEliminatedCount()).isZero();
        assertThat(decision.getPassed()).isTrue();
        assertThat(decision.getEliminatedReason()).isEqualTo(EliminationReason.NONE);
        assertThat(result.getPassedSymbols()).extracting(SymbolInfo::getSymbol).containsExactly("BTCUSDT");
    }

    private PreFilterService service() {
        ScannerProperties properties = new ScannerProperties();
        ScannerProperties.Liquidity liquidity = new ScannerProperties.Liquidity();
        liquidity.setMinQuoteVolume24h(new BigDecimal("30000000"));
        liquidity.setMaxSpreadPct(new BigDecimal("0.08"));
        liquidity.setMaxPump24hPct(new BigDecimal("25"));
        liquidity.setMaxDump24hPct(new BigDecimal("-25"));
        properties.setLiquidity(liquidity);
        return new PreFilterService(properties);
    }

    private SymbolInfo symbol(String symbol) {
        return SymbolInfo.builder()
                .symbol(symbol)
                .build();
    }

    private Ticker24h ticker(String symbol, String quoteVolume, String priceChangePercent) {
        return Ticker24h.builder()
                .symbol(symbol)
                .quoteVolume(new BigDecimal(quoteVolume))
                .priceChangePercent(new BigDecimal(priceChangePercent))
                .build();
    }

    private BookTicker bookTicker(String symbol, String spreadPct) {
        return BookTicker.builder()
                .symbol(symbol)
                .spreadPct(new BigDecimal(spreadPct))
                .build();
    }
}
