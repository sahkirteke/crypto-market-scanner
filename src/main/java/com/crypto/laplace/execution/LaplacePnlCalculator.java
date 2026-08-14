package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class LaplacePnlCalculator {
    private static final int SCALE = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal gross(PositionSide side, BigDecimal entry, BigDecimal exit, BigDecimal quantity) {
        return directionalMove(side, entry, exit).multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal priceMovePct(PositionSide side, BigDecimal entry, BigDecimal exit) {
        return directionalMove(side, entry, exit)
                .divide(entry, SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    public Pnl calculate(PositionSide side, BigDecimal entry, BigDecimal exit, BigDecimal quantity,
                         BigDecimal entryNotional, BigDecimal entryFee, BigDecimal exitFee) {
        BigDecimal gross = gross(side, entry, exit, quantity);
        BigDecimal grossPct = gross.divide(entryNotional, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
        BigDecimal net = gross.subtract(entryFee).subtract(exitFee);
        BigDecimal netPct = net.divide(entryNotional, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
        return new Pnl(gross, grossPct, net, netPct);
    }

    public record Pnl(BigDecimal gross, BigDecimal grossPct, BigDecimal net, BigDecimal netPct) {
    }

    private BigDecimal directionalMove(PositionSide side, BigDecimal entry, BigDecimal exit) {
        return side == PositionSide.LONG ? exit.subtract(entry) : entry.subtract(exit);
    }
}
