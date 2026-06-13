package com.crypto.scanner.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.scanner.config.ScannerProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KlineService {
    private static final String ONE_HOUR_INTERVAL = "1h";
    private static final String FOUR_HOUR_INTERVAL = "4h";

    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    public KlineBundle loadForSymbol(String symbol) {
        KlineBundle bundle = KlineBundle.builder()
                .symbol(symbol)
                .build();

        ScannerProperties.Klines klineProperties = klines();
        int oneHourLimit = valueOrDefault(klineProperties.getOneHourLimit(), 250);
        int fourHourLimit = valueOrDefault(klineProperties.getFourHourLimit(), 250);
        int minClosedCandles = valueOrDefault(klineProperties.getMinClosedCandles(), 220);

        try {
            List<Kline> oneHourClosedKlines = removeOpenCandles(
                    binanceFuturesClient.getKlines(symbol, ONE_HOUR_INTERVAL, oneHourLimit));
            List<Kline> fourHourClosedKlines = removeOpenCandles(
                    binanceFuturesClient.getKlines(symbol, FOUR_HOUR_INTERVAL, fourHourLimit));

            bundle.setOneHourKlines(oneHourClosedKlines);
            bundle.setFourHourKlines(fourHourClosedKlines);

            int oneHourClosedCount = oneHourClosedKlines.size();
            int fourHourClosedCount = fourHourClosedKlines.size();

            log.info("KLINES_READY symbol={} interval={} requested={} closed={}",
                    symbol, ONE_HOUR_INTERVAL, oneHourLimit, oneHourClosedCount);
            log.info("KLINES_READY symbol={} interval={} requested={} closed={}",
                    symbol, FOUR_HOUR_INTERVAL, fourHourLimit, fourHourClosedCount);

            if (oneHourClosedCount >= minClosedCandles && fourHourClosedCount >= minClosedCandles) {
                bundle.setReady(true);
                bundle.setEliminatedReason(EliminationReason.NONE);
                return bundle;
            }

            markNotReady(bundle, EliminationReason.DATA_NOT_READY);
            log.info("KLINES_NOT_READY symbol={} oneHourClosed={} fourHourClosed={} minRequired={}",
                    symbol, oneHourClosedCount, fourHourClosedCount, minClosedCandles);
            return bundle;
        } catch (Exception exception) {
            markNotReady(bundle, EliminationReason.DATA_ERROR);
            log.error("KLINE_LOAD_ERROR symbol={} message={}", symbol, exception.getMessage(), exception);
            return bundle;
        }
    }

    public KlineLoadResult loadForSymbols(List<SymbolInfo> symbols) {
        List<SymbolInfo> safeSymbols = symbols == null ? Collections.emptyList() : symbols;
        List<KlineBundle> readyBundles = new ArrayList<>();
        List<KlineBundle> notReadyBundles = new ArrayList<>();

        for (SymbolInfo symbolInfo : safeSymbols) {
            if (symbolInfo == null) {
                continue;
            }
            KlineBundle bundle = loadForSymbol(symbolInfo.getSymbol());
            if (Boolean.TRUE.equals(bundle.getReady())) {
                readyBundles.add(bundle);
            } else {
                notReadyBundles.add(bundle);
            }
        }

        KlineLoadResult result = KlineLoadResult.builder()
                .readyBundles(readyBundles)
                .notReadyBundles(notReadyBundles)
                .totalCount(readyBundles.size() + notReadyBundles.size())
                .readyCount(readyBundles.size())
                .notReadyCount(notReadyBundles.size())
                .build();

        log.info("KLINE_LOAD_DONE total={} ready={} notReady={}",
                result.getTotalCount(), result.getReadyCount(), result.getNotReadyCount());
        return result;
    }

    public List<Kline> removeOpenCandles(List<Kline> klines) {
        if (klines == null || klines.isEmpty()) {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        return klines.stream()
                .filter(kline -> kline != null && kline.getCloseTime() != null)
                .filter(kline -> !kline.getCloseTime().isAfter(now))
                .filter(kline -> Boolean.TRUE.equals(kline.getClosed()))
                .toList();
    }

    private void markNotReady(KlineBundle bundle, EliminationReason eliminatedReason) {
        bundle.setReady(false);
        bundle.setEliminatedReason(eliminatedReason);
        if (!bundle.getReasons().contains(ReasonTag.DATA_NOT_READY)) {
            bundle.getReasons().add(ReasonTag.DATA_NOT_READY);
        }
    }

    private ScannerProperties.Klines klines() {
        if (scannerProperties.getKlines() == null) {
            scannerProperties.setKlines(new ScannerProperties.Klines());
        }
        return scannerProperties.getKlines();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
