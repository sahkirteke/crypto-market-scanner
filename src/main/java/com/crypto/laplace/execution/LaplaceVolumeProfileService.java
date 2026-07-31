package com.crypto.laplace.execution;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LaplaceVolumeProfileService {
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    public static final int NY_SESSION_5M_CANDLE_COUNT = 78;
    public static final int CURRENT_DELTA_60M_CANDLE_COUNT = 12;
    static final int ROW_COUNT = 12, REQUIRED_SESSION_CANDLES = NY_SESSION_5M_CANDLE_COUNT, REQUIRED_DELTA_CANDLES = CURRENT_DELTA_60M_CANDLE_COUNT;
    private final BinanceFuturesClient client;
    private final Map<SessionCacheKey,SessionProfile> sessionCache=new ConcurrentHashMap<>();

    public LaplaceVolumeProfileService(BinanceFuturesClient client) { this.client=client; }

    public WarmupResult prewarm(String symbol,Instant now) {
        long started=System.nanoTime();LocalDate date=now.atZone(NEW_YORK).toLocalDate().minusDays(1);SessionWindow window=session(date);SessionCacheKey key=new SessionCacheKey(symbol,date);
        boolean hit=sessionCache.containsKey(key);int loadedSession=REQUIRED_SESSION_CANDLES;
        if(!hit){List<Kline> sessionCandles=completed(client.getKlines(symbol,"5m",REQUIRED_SESSION_CANDLES,window.start()),now).stream().filter(c->!c.getOpenTime().isBefore(window.start())&&c.getCloseTime().isBefore(window.end())).toList();loadedSession=sessionCandles.size();if(loadedSession!=REQUIRED_SESSION_CANDLES)throw new IllegalStateException("VP_PROFILE_NOT_AVAILABLE");sessionCache.putIfAbsent(key,buildProfile(window,sessionCandles));evictOldProfiles(symbol,date);}
        List<Kline> current=completed(client.getKlines(symbol,"5m",CURRENT_DELTA_60M_CANDLE_COUNT),now);if(current.size()!=CURRENT_DELTA_60M_CANDLE_COUNT)throw new IllegalStateException("VP_INSUFFICIENT_60M_DATA");
        return new WarmupResult(date,loadedSession,current.size(),hit,!hit,(System.nanoTime()-started)/1_000_000);
    }

    public LaplaceVolumeProfileDecision evaluate(String symbol,Instant signalTime,double entryPrice,PositionSide side) {
        try {
        SessionProfile profile=findPreviousProfile(symbol,signalTime);
        if(profile==null)return unavailable(entryPrice,VolumeProfileRejectionReason.VP_PROFILE_NOT_AVAILABLE);
        boolean inside=entryPrice>=profile.low()&&entryPrice<=profile.high();
        Double local=inside?profile.rowAt(entryPrice).deltaRatio():null;
        Double delta60=null;
        if(!inside){List<Kline> recent=completed(client.getKlines(symbol,"5m",20,signalTime.minusSeconds(65*60)),signalTime);
            delta60=recent.size()>=REQUIRED_DELTA_CANDLES?delta60(recent.subList(recent.size()-REQUIRED_DELTA_CANDLES,recent.size())):null;}
        boolean insideLong=side==PositionSide.LONG&&inside&&entryPrice<=profile.poc()&&local<=0;
        boolean insideShort=side==PositionSide.SHORT&&inside&&entryPrice>=profile.poc()&&local>=0;
        boolean outsideLong=side==PositionSide.LONG&&entryPrice<profile.low()&&delta60!=null&&delta60>=0;
        boolean outsideShort=side==PositionSide.SHORT&&entryPrice>profile.high()&&delta60!=null&&delta60<=0;
        boolean allowed=insideLong||insideShort||outsideLong||outsideShort;
        VolumeProfileRejectionReason reason=allowed?null:reason(side,entryPrice,profile,inside,local,delta60);
        return new LaplaceVolumeProfileDecision(profile.date(),profile.start(),profile.end(),profile.low(),profile.high(),profile.poc(),ROW_COUNT,
                entryPrice,inside,local,delta60,insideLong,insideShort,outsideLong,outsideShort,allowed,reason);
        } catch(RuntimeException unavailable) {
            return unavailable(entryPrice,VolumeProfileRejectionReason.VP_PROFILE_NOT_AVAILABLE);
        }
    }

    SessionProfile findPreviousProfile(String symbol,Instant signalTime) {
        LocalDate date=signalTime.atZone(NEW_YORK).toLocalDate().minusDays(1);
        for(int day=0;day<10;day++,date=date.minusDays(1)){
            SessionWindow window=session(date);
            if(window.end().isAfter(signalTime))continue;
            SessionCacheKey key=new SessionCacheKey(symbol,date);
            SessionProfile cached=sessionCache.get(key);
            if(cached!=null)return cached;
            List<Kline> candles=completed(client.getKlines(symbol,"5m",REQUIRED_SESSION_CANDLES,window.start()),signalTime).stream()
                    .filter(c->!c.getOpenTime().isBefore(window.start())&&c.getCloseTime().isBefore(window.end())).toList();
            if(candles.size()==REQUIRED_SESSION_CANDLES){SessionProfile profile=buildProfile(window,candles);sessionCache.putIfAbsent(key,profile);evictOldProfiles(symbol,date);return sessionCache.get(key);}
        }
        return null;
    }

    static SessionWindow session(LocalDate date){return new SessionWindow(date,date.atTime(9,30).atZone(NEW_YORK).toInstant(),date.atTime(16,0).atZone(NEW_YORK).toInstant());}
    static SessionProfile buildProfile(SessionWindow window,List<Kline> candles){
        if(candles.size()<REQUIRED_SESSION_CANDLES)throw new IllegalArgumentException("VP_PROFILE_NOT_AVAILABLE");
        double low=candles.stream().mapToDouble(c->c.getLow().doubleValue()).min().orElseThrow();
        double high=candles.stream().mapToDouble(c->c.getHigh().doubleValue()).max().orElseThrow();
        if(!(high>low))throw new IllegalArgumentException("VP_PROFILE_NOT_AVAILABLE");
        double width=(high-low)/ROW_COUNT;double[] quote=new double[ROW_COUNT],buy=new double[ROW_COUNT];
        for(Kline c:candles){double candleLow=c.getLow().doubleValue(),candleHigh=c.getHigh().doubleValue();double range=candleHigh-candleLow;
            for(int i=0;i<ROW_COUNT;i++){double rowLow=low+i*width,rowHigh=i==ROW_COUNT-1?high:rowLow+width;double overlap=Math.max(0,Math.min(candleHigh,rowHigh)-Math.max(candleLow,rowLow));
                double share=range>0?overlap/range:(candleLow>=rowLow&&candleLow<=rowHigh?1:0);quote[i]+=value(c.getQuoteAssetVolume())*share;buy[i]+=value(c.getTakerBuyQuoteVolume())*share;}}
        List<ProfileRow> rows=new ArrayList<>();int poc=0;for(int i=0;i<ROW_COUNT;i++){double rowLow=low+i*width,rowHigh=i==ROW_COUNT-1?high:rowLow+width;double ratio=quote[i]>0?(2*buy[i]-quote[i])/quote[i]:0;rows.add(new ProfileRow(rowLow,rowHigh,quote[i],buy[i],ratio));if(quote[i]>quote[poc])poc=i;}
        return new SessionProfile(window.date(),window.start(),window.end(),low,high,rows.get(poc).mid(),List.copyOf(rows));
    }
    static double delta60(List<Kline> candles){double quote=candles.stream().mapToDouble(c->value(c.getQuoteAssetVolume())).sum(),buy=candles.stream().mapToDouble(c->value(c.getTakerBuyQuoteVolume())).sum();return quote>0?(2*buy-quote)/quote:0;}
    private static List<Kline> completed(List<Kline> candles,Instant before){return candles.stream().filter(Objects::nonNull).filter(c->c.getCloseTime()!=null&&!c.getCloseTime().isAfter(before)&&!Boolean.FALSE.equals(c.getClosed())).sorted(Comparator.comparing(Kline::getOpenTime)).toList();}
    private static double value(java.math.BigDecimal value){return value==null?0:value.doubleValue();}
    private void evictOldProfiles(String symbol,LocalDate newest){sessionCache.keySet().removeIf(k->k.symbol().equals(symbol)&&k.nySessionDate().isBefore(newest.minusDays(3)));}
    private static VolumeProfileRejectionReason reason(PositionSide side,double price,SessionProfile p,boolean inside,Double local,Double delta){
        if(side==PositionSide.LONG&&price>p.high())return VolumeProfileRejectionReason.VP_LONG_ABOVE_SESSION_HIGH;
        if(side==PositionSide.SHORT&&price<p.low())return VolumeProfileRejectionReason.VP_SHORT_BELOW_SESSION_LOW;
        if(!inside&&delta==null)return VolumeProfileRejectionReason.VP_INSUFFICIENT_60M_DATA;
        if(side==PositionSide.LONG&&!inside)return VolumeProfileRejectionReason.VP_OUTSIDE_LONG_NO_BUY_CONFIRMATION;
        if(side==PositionSide.SHORT&&!inside)return VolumeProfileRejectionReason.VP_OUTSIDE_SHORT_NO_SELL_CONFIRMATION;
        if(side==PositionSide.LONG&&price>p.poc())return VolumeProfileRejectionReason.VP_INSIDE_LONG_ABOVE_POC;
        if(side==PositionSide.LONG&&local>0)return VolumeProfileRejectionReason.VP_INSIDE_LONG_DELTA_NOT_SELLING;
        if(side==PositionSide.SHORT&&price<p.poc())return VolumeProfileRejectionReason.VP_INSIDE_SHORT_BELOW_POC;
        return VolumeProfileRejectionReason.VP_INSIDE_SHORT_DELTA_NOT_BUYING;
    }
    private static LaplaceVolumeProfileDecision unavailable(double entry,VolumeProfileRejectionReason reason){return new LaplaceVolumeProfileDecision(null,null,null,Double.NaN,Double.NaN,Double.NaN,ROW_COUNT,entry,false,null,null,false,false,false,false,false,reason);}
    record SessionWindow(LocalDate date,Instant start,Instant end){}
    record SessionCacheKey(String symbol,LocalDate nySessionDate){}
    public record WarmupResult(LocalDate nySessionDate,int loadedSessionCandleCount,int loadedCurrentCandleCount,boolean profileCacheHit,boolean profileCacheMiss,long durationMs){}
    record ProfileRow(double low,double high,double quoteVolume,double takerBuyQuoteVolume,double deltaRatio){double mid(){return (low+high)/2;}}
    record SessionProfile(LocalDate date,Instant start,Instant end,double low,double high,double poc,List<ProfileRow> rows){ProfileRow rowAt(double price){if(price<low||price>high)return null;int index=price>=high?rows.size()-1:(int)((price-low)/((high-low)/rows.size()));return rows.get(index);}}
}
