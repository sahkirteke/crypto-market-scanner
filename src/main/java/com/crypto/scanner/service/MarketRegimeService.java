package com.crypto.scanner.service;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.MarketRegimeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRegimeService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 10;
    private static final int OUTPUT_SCALE = 2;

    private final ScannerProperties scannerProperties;

    public MarketRegimeResult calculate(
            TechnicalSnapshot btcOneHour,
            TechnicalSnapshot btcFourHour,
            TechnicalSnapshot ethFourHour,
            Ticker24h btcTicker,
            BigDecimal marketBreadthPct,
            BigDecimal btcFourHourChangePct
    ) {
        BigDecimal safeMarketBreadthPct = marketBreadthPct == null ? BigDecimal.ZERO : marketBreadthPct;
        MarketRegimeResult result = MarketRegimeResult.builder()
                .marketBreadthPct(safeMarketBreadthPct)
                .build();

        if (hasMissingCriticalData(btcOneHour, btcFourHour, ethFourHour, btcTicker, btcFourHourChangePct)) {
            setChop(result);
            addReason(result, ReasonTag.MARKET_CHOP);
            addNote(result, "DATA_NOT_READY");
            logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
            return result;
        }

        ScannerProperties.MarketRegime properties = marketRegimeProperties();
        BigDecimal btc24hChange = btcTicker.getPriceChangePercent();
        BigDecimal btcOneHourVolumeRatio = btcOneHour.getVolumeRatio();

        boolean panicRuleA = btc24hChange.compareTo(properties.getPanicBtc24hDropPct()) <= 0
                && isBelowEma20(btcFourHour)
                && btcOneHourVolumeRatio.compareTo(properties.getPanicVolumeRatio1h()) > 0;
        boolean panicRuleB = btcFourHourChangePct.compareTo(properties.getPanicBtc4hDropPct()) <= 0
                && btcOneHourVolumeRatio.compareTo(properties.getPanicVolumeRatio4hRule()) > 0;

        if (panicRuleA || panicRuleB) {
            result.setMarketRegime(MarketRegime.PANIC);
            result.setBlockNewLong(true);
            result.setBlockNewShort(true);
            addReason(result, ReasonTag.MARKET_PANIC);
            if (panicRuleA) {
                addNote(result, "PANIC_RULE_A");
            }
            if (panicRuleB) {
                addNote(result, "PANIC_RULE_B");
            }
            String panicRule = String.join(",", result.getNotes());
            log.info("MARKET_REGIME_PANIC rule={} btc24hChange={} btcFourHourChangePct={} volumeRatio1h={}",
                    panicRule, btc24hChange, btcFourHourChangePct, btcOneHourVolumeRatio);
            logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
            return result;
        }

        boolean btcAboveEma = isAboveEma20(btcFourHour);
        boolean ethAboveEma = isAboveEma20(ethFourHour);
        boolean riskOn = btcAboveEma
                && ethAboveEma
                && isPositive(btcFourHour.getMacdHist())
                && isPositive(ethFourHour.getMacdHist())
                && safeMarketBreadthPct.compareTo(properties.getBreadthRiskOnMinPct()) >= 0;

        if (riskOn) {
            result.setMarketRegime(MarketRegime.RISK_ON);
            addReason(result, ReasonTag.MARKET_RISK_ON);
            if (safeMarketBreadthPct.compareTo(properties.getBreadthStrongPct()) >= 0) {
                addReason(result, ReasonTag.MARKET_BREADTH_STRONG);
            } else {
                addReason(result, ReasonTag.MARKET_BREADTH_OK);
            }
            logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
            return result;
        }

        if (btcAboveEma && ethAboveEma
                && safeMarketBreadthPct.compareTo(properties.getBreadthRiskOnMinPct()) < 0) {
            setChop(result);
            addReason(result, ReasonTag.MARKET_CHOP);
            addReason(result, ReasonTag.WEAK_RISK_ON_BREADTH_FAIL);
            logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
            return result;
        }

        boolean riskOffRuleA = isBelowEma20(btcFourHour)
                && isBelowEma20(ethFourHour)
                && isNegative(btcFourHour.getMacdHist());
        boolean riskOffRuleB = safeMarketBreadthPct.compareTo(properties.getBreadthRiskOffMaxPct()) < 0
                && isBelowEma20(btcFourHour);

        if (riskOffRuleA || riskOffRuleB) {
            result.setMarketRegime(MarketRegime.RISK_OFF);
            addReason(result, ReasonTag.MARKET_RISK_OFF);
            logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
            return result;
        }

        setChop(result);
        addReason(result, ReasonTag.MARKET_CHOP);
        logReady(result, safeMarketBreadthPct, btcFourHourChangePct);
        return result;
    }

    public BigDecimal calculateBtcFourHourChangePct(TechnicalSnapshot btcFourHour) {
        if (btcFourHour == null || btcFourHour.getClose() == null || btcFourHour.getPreviousClose() == null
                || BigDecimal.ZERO.compareTo(btcFourHour.getPreviousClose()) == 0) {
            return null;
        }

        return btcFourHour.getClose()
                .subtract(btcFourHour.getPreviousClose())
                .divide(btcFourHour.getPreviousClose(), CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    private boolean hasMissingCriticalData(
            TechnicalSnapshot btcOneHour,
            TechnicalSnapshot btcFourHour,
            TechnicalSnapshot ethFourHour,
            Ticker24h btcTicker,
            BigDecimal btcFourHourChangePct
    ) {
        return btcOneHour == null
                || btcOneHour.getVolumeRatio() == null
                || btcFourHour == null
                || btcFourHour.getClose() == null
                || btcFourHour.getEma20() == null
                || btcFourHour.getMacdHist() == null
                || ethFourHour == null
                || ethFourHour.getClose() == null
                || ethFourHour.getEma20() == null
                || ethFourHour.getMacdHist() == null
                || btcTicker == null
                || btcTicker.getPriceChangePercent() == null
                || btcFourHourChangePct == null;
    }

    private ScannerProperties.MarketRegime marketRegimeProperties() {
        if (scannerProperties.getMarketRegime() == null) {
            scannerProperties.setMarketRegime(new ScannerProperties.MarketRegime());
        }
        return scannerProperties.getMarketRegime();
    }

    private boolean isAboveEma20(TechnicalSnapshot snapshot) {
        return snapshot.getClose().compareTo(snapshot.getEma20()) > 0;
    }

    private boolean isBelowEma20(TechnicalSnapshot snapshot) {
        return snapshot.getClose().compareTo(snapshot.getEma20()) < 0;
    }

    private boolean isPositive(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    private void setChop(MarketRegimeResult result) {
        result.setMarketRegime(MarketRegime.CHOP);
        result.setBlockNewLong(false);
        result.setBlockNewShort(false);
    }

    private void addReason(MarketRegimeResult result, ReasonTag reasonTag) {
        if (!result.getReasonTags().contains(reasonTag)) {
            result.getReasonTags().add(reasonTag);
        }
    }

    private void addNote(MarketRegimeResult result, String note) {
        if (!result.getNotes().contains(note)) {
            result.getNotes().add(note);
        }
    }

    private void logReady(MarketRegimeResult result, BigDecimal marketBreadthPct, BigDecimal btcFourHourChangePct) {
        log.info("MARKET_REGIME_READY regime={} marketBreadthPct={} btcFourHourChangePct={} reasons={}",
                result.getMarketRegime(), marketBreadthPct, btcFourHourChangePct, result.getReasonTags());
    }
}
