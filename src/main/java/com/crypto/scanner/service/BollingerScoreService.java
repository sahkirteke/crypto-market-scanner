package com.crypto.scanner.service;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.scanner.model.BollingerScoreResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BollingerScoreService {
    private static final BigDecimal MIN_SCORE = new BigDecimal("-8");
    private static final BigDecimal MAX_SCORE = new BigDecimal("5");

    public BollingerScoreResult calculate(
            PositionSide side,
            MarketRegime marketRegime,
            TechnicalSnapshot current1h,
            TechnicalSnapshot previous1h
    ) {
        if (current1h == null || current1h.getBbPercentB() == null) {
            return zero(current1h);
        }

        BigDecimal score = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        BigDecimal percentB = current1h.getBbPercentB();

        if (side == PositionSide.LONG) {
            if (ge(percentB, "0.90")) {
                score = score.subtract(new BigDecimal("4"));
                reasons.add("LONG_BB_CHASE_RISK");
            }
            if (Boolean.TRUE.equals(current1h.getBbUpperClosedOutside())) {
                score = score.subtract(new BigDecimal("6"));
                reasons.add("LONG_BB_OUTSIDE_CHASE");
            }
            if (Boolean.TRUE.equals(current1h.getBbUpperTouched()) && !Boolean.TRUE.equals(current1h.getBbUpperClosedOutside())) {
                score = score.subtract(new BigDecimal("3"));
                reasons.add("LONG_UPPER_WICK_REJECTION");
            }
            if (previous1h != null) {
                if (between(percentB, "0.35", "0.65")
                        && gt(current1h.getClose(), current1h.getEma20())
                        && gt(current1h.getMacdHist(), previous1h.getMacdHist())) {
                    score = score.add(new BigDecimal("4"));
                    reasons.add("LONG_BB_HEALTHY_PULLBACK");
                }
                if (marketRegime == MarketRegime.CHOP
                        && le(percentB, "0.30")
                        && gt(current1h.getRsi14(), previous1h.getRsi14())
                        && gt(current1h.getMacdHist(), previous1h.getMacdHist())) {
                    score = score.add(new BigDecimal("4"));
                    reasons.add("LONG_BB_MEAN_REVERSION");
                }
            }
        } else if (side == PositionSide.SHORT) {
            if (le(percentB, "0.10")) {
                score = score.subtract(new BigDecimal("6"));
                reasons.add("SHORT_BB_LOWER_CHASE_RISK");
            }
            if (Boolean.TRUE.equals(current1h.getBbLowerClosedOutside())) {
                score = score.subtract(new BigDecimal("8"));
                reasons.add("SHORT_BB_OUTSIDE_CHASE");
            }
            if (Boolean.TRUE.equals(current1h.getBbLowerTouched()) && !Boolean.TRUE.equals(current1h.getBbLowerClosedOutside())) {
                score = score.subtract(new BigDecimal("4"));
                reasons.add("SHORT_LOWER_WICK_REJECTION");
            }
            if (previous1h != null) {
                if (between(percentB, "0.60", "0.85")
                        && lt(current1h.getClose(), current1h.getEma20())
                        && lt(current1h.getMacdHist(), previous1h.getMacdHist())) {
                    score = score.add(new BigDecimal("4"));
                    reasons.add("SHORT_BB_HEALTHY_REJECTION");
                }
                if (marketRegime == MarketRegime.CHOP
                        && ge(percentB, "0.75")
                        && lt(current1h.getRsi14(), previous1h.getRsi14())
                        && lt(current1h.getMacdHist(), previous1h.getMacdHist())) {
                    score = score.add(new BigDecimal("4"));
                    reasons.add("SHORT_BB_MEAN_REVERSION");
                }
            }
        }

        return result(current1h, clamp(score), reasons);
    }

    private BollingerScoreResult zero(TechnicalSnapshot snapshot) {
        return result(snapshot, BigDecimal.ZERO, new ArrayList<>());
    }

    private BollingerScoreResult result(TechnicalSnapshot snapshot, BigDecimal score, List<String> reasons) {
        return BollingerScoreResult.builder()
                .bbScore(score)
                .bbReasons(reasons)
                .bbPercentB(snapshot == null ? null : snapshot.getBbPercentB())
                .bbWidth(snapshot == null ? null : snapshot.getBbWidth())
                .bbUpper(snapshot == null ? null : snapshot.getBbUpper())
                .bbMiddle(snapshot == null ? null : snapshot.getBbMiddle())
                .bbLower(snapshot == null ? null : snapshot.getBbLower())
                .bbUpperTouched(snapshot == null ? null : snapshot.getBbUpperTouched())
                .bbLowerTouched(snapshot == null ? null : snapshot.getBbLowerTouched())
                .bbUpperClosedOutside(snapshot == null ? null : snapshot.getBbUpperClosedOutside())
                .bbLowerClosedOutside(snapshot == null ? null : snapshot.getBbLowerClosedOutside())
                .build();
    }

    private BigDecimal clamp(BigDecimal score) {
        if (score.compareTo(MIN_SCORE) < 0) {
            return MIN_SCORE;
        }
        if (score.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        return score;
    }

    private boolean between(BigDecimal value, String min, String max) {
        return value != null && value.compareTo(new BigDecimal(min)) >= 0 && value.compareTo(new BigDecimal(max)) <= 0;
    }

    private boolean ge(BigDecimal value, String threshold) { return value != null && value.compareTo(new BigDecimal(threshold)) >= 0; }
    private boolean le(BigDecimal value, String threshold) { return value != null && value.compareTo(new BigDecimal(threshold)) <= 0; }
    private boolean gt(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) > 0; }
    private boolean lt(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) < 0; }
}
