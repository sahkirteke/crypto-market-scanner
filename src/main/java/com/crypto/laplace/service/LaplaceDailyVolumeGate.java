package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceDailyVolumeGate {
    private final BinanceFuturesClient client;
    private final LaplaceStrategyProperties properties;
    public boolean allows(String symbol, Instant signalTime) {
        BigDecimal threshold=properties.getLaplace().getMinDailyQuoteVolumeUsdt();
        try {
            var ticker=client.getAll24hTickers().stream().filter(t->symbol.equals(t.getSymbol())).findFirst().orElse(null);
            BigDecimal volume=ticker==null?null:ticker.getQuoteVolume(); Instant timestamp=ticker==null?null:ticker.getCloseTime();
            boolean allowed=volume!=null&&volume.compareTo(threshold)>0;
            log.info("DAILY_VOLUME_CHECK symbol={} quoteVolume={} threshold={} tickerTimestamp={} signalTime={} allowed={} reason={}",
                    symbol,volume,threshold,timestamp,signalTime,allowed,allowed?"ALLOWED":"DAILY_QUOTE_VOLUME_BELOW_20M");
            return allowed;
        } catch(RuntimeException e) {
            log.warn("DAILY_VOLUME_CHECK symbol={} quoteVolume=null threshold={} tickerTimestamp=null signalTime={} allowed=false reason=DAILY_QUOTE_VOLUME_BELOW_20M",symbol,threshold,signalTime);
            return false;
        }
    }
}
