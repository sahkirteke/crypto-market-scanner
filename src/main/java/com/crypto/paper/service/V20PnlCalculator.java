package com.crypto.paper.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.V20PnlResult;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class V20PnlCalculator {
    private static final int SCALE = 8;
    private static final int PRICE_SCALE = 12;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private final ScannerProperties scannerProperties;

    public V20PnlCalculator(ScannerProperties scannerProperties) { this.scannerProperties = scannerProperties; }

    public BigDecimal marginUsdt() { return paper().getMarginUsdt() == null ? BigDecimal.valueOf(100) : paper().getMarginUsdt(); }
    public int leverage() { return paper().getLeverage() == null ? 5 : paper().getLeverage(); }
    public BigDecimal leveragedNotionalUsdt() { return marginUsdt().multiply(BigDecimal.valueOf(leverage())).setScale(SCALE, RoundingMode.HALF_UP); }
    public BigDecimal feeRate() {
        ScannerProperties.Fee fee = paper().getFee();
        if (fee != null && "TAKER".equalsIgnoreCase(fee.getMode())) return fee.getTakerFeePct();
        if (fee != null && fee.getMakerFeePct() != null) return fee.getMakerFeePct();
        return new BigDecimal("0.0002");
    }
    public String feeMode() { return paper().getFee() == null || paper().getFee().getMode() == null ? "MAKER" : paper().getFee().getMode(); }
    public BigDecimal slippagePct() { return paper().getFee() == null || paper().getFee().getSlippagePct() == null ? new BigDecimal("0.0005") : paper().getFee().getSlippagePct(); }

    public V20PnlResult calculate(PositionSide side, BigDecimal entryPrice, BigDecimal exitPrice) {
        BigDecimal margin = marginUsdt();
        BigDecimal leveragedNotional = leveragedNotionalUsdt();
        BigDecimal unleveragedQuantity = margin.divide(entryPrice, PRICE_SCALE, RoundingMode.DOWN);
        BigDecimal leveragedQuantity = leveragedNotional.divide(entryPrice, PRICE_SCALE, RoundingMode.DOWN);
        return calculate(side, entryPrice, exitPrice, unleveragedQuantity, leveragedQuantity);
    }

    public V20PnlResult calculate(PositionSide side, BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal unleveragedQuantity, BigDecimal leveragedQuantity) {
        BigDecimal margin = marginUsdt();
        BigDecimal leveragedNotional = margin.multiply(BigDecimal.valueOf(leverage())).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal feeRate = feeRate();
        BigDecimal slip = slippagePct();
        BigDecimal entryAdjusted = side == PositionSide.SHORT ? entryPrice.multiply(BigDecimal.ONE.subtract(slip)) : entryPrice.multiply(BigDecimal.ONE.add(slip));
        BigDecimal exitAdjusted = side == PositionSide.SHORT ? exitPrice.multiply(BigDecimal.ONE.add(slip)) : exitPrice.multiply(BigDecimal.ONE.subtract(slip));
        BigDecimal rawPct = ratio(side == PositionSide.SHORT ? entryPrice.subtract(exitPrice) : exitPrice.subtract(entryPrice), entryPrice).multiply(ONE_HUNDRED).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unRaw = (side == PositionSide.SHORT ? entryPrice.subtract(exitPrice) : exitPrice.subtract(entryPrice)).multiply(unleveragedQuantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levRaw = (side == PositionSide.SHORT ? entryPrice.subtract(exitPrice) : exitPrice.subtract(entryPrice)).multiply(leveragedQuantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unEntryFee = entryAdjusted.multiply(unleveragedQuantity).multiply(feeRate).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unExitFee = exitAdjusted.multiply(unleveragedQuantity).multiply(feeRate).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levEntryFee = entryAdjusted.multiply(leveragedQuantity).multiply(feeRate).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levExitFee = exitAdjusted.multiply(leveragedQuantity).multiply(feeRate).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unGrossAdj = (side == PositionSide.SHORT ? entryAdjusted.subtract(exitAdjusted) : exitAdjusted.subtract(entryAdjusted)).multiply(unleveragedQuantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levGrossAdj = (side == PositionSide.SHORT ? entryAdjusted.subtract(exitAdjusted) : exitAdjusted.subtract(entryAdjusted)).multiply(leveragedQuantity).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unFees = unEntryFee.add(unExitFee).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levFees = levEntryFee.add(levExitFee).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal unNet = unGrossAdj.subtract(unFees).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal levNet = levGrossAdj.subtract(levFees).setScale(SCALE, RoundingMode.HALF_UP);
        return new V20PnlResult(rawPct, entryAdjusted.setScale(PRICE_SCALE, RoundingMode.HALF_UP), exitAdjusted.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                margin.setScale(SCALE, RoundingMode.HALF_UP), leveragedNotional, unleveragedQuantity, leveragedQuantity,
                unRaw, unEntryFee, unExitFee, unFees, unNet, ratio(unNet, margin).multiply(ONE_HUNDRED).setScale(SCALE, RoundingMode.HALF_UP),
                levRaw, levEntryFee, levExitFee, levFees, levNet, ratio(levNet, margin).multiply(ONE_HUNDRED).setScale(SCALE, RoundingMode.HALF_UP),
                feeRate, feeMode(), slip);
    }

    private ScannerProperties.Paper paper() { return scannerProperties.getPaper(); }
    private BigDecimal ratio(BigDecimal a, BigDecimal b) { return a.divide(b, SCALE + 4, RoundingMode.HALF_UP); }
}
