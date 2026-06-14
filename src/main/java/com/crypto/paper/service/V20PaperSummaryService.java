package com.crypto.paper.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.V20PaperSummary;
import com.crypto.paper.model.V20PaperSummaryReport;
import com.crypto.persistence.entity.PaperPositionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class V20PaperSummaryService {
    private static final int SCALE = 8;
    private final V20PnlCalculator pnlCalculator;

    public V20PaperSummaryService(V20PnlCalculator pnlCalculator) { this.pnlCalculator = pnlCalculator; }

    public V20PaperSummaryReport summarize(List<PaperPositionEntity> positions) {
        List<PaperPositionEntity> v20 = (positions == null ? List.<PaperPositionEntity>of() : positions).stream()
                .filter(p -> p != null && "V20".equalsIgnoreCase(p.getStrategyVersion()))
                .filter(this::isValidClosedV20Trade)
                .toList();
        return new V20PaperSummaryReport(summary("LONG", v20, PositionSide.LONG), summary("SHORT", v20, PositionSide.SHORT), summary("TOTAL", v20, null));
    }

    private V20PaperSummary summary(String label, List<PaperPositionEntity> positions, PositionSide side) {
        List<PaperPositionEntity> selected = positions.stream().filter(p -> side == null || p.getSide() == side).toList();
        int wins = (int) selected.stream().filter(p -> value(p.getLeveragedNetPnlUsdt()).compareTo(BigDecimal.ZERO) > 0).count();
        int losses = (int) selected.stream().filter(p -> value(p.getLeveragedNetPnlUsdt()).compareTo(BigDecimal.ZERO) < 0).count();
        int tp = (int) selected.stream().filter(p -> "TAKE_PROFIT".equals(p.getExitReason())).count();
        int sl = (int) selected.stream().filter(p -> "STOP_LOSS".equals(p.getExitReason())).count();
        BigDecimal unFee = sum(selected.stream().map(p -> value(p.getUnleveragedTotalFeeUsdt())).toList());
        BigDecimal unNet = sum(selected.stream().map(p -> value(p.getUnleveragedNetPnlUsdt())).toList());
        BigDecimal levFee = sum(selected.stream().map(p -> value(p.getLeveragedTotalFeeUsdt())).toList());
        BigDecimal levNet = sum(selected.stream().map(p -> value(p.getLeveragedNetPnlUsdt())).toList());
        BigDecimal totalMargin = pnlCalculator.marginUsdt().multiply(BigDecimal.valueOf(selected.size()));
        return new V20PaperSummary(label, selected.size(), wins, losses, pct(BigDecimal.valueOf(wins), BigDecimal.valueOf(selected.size())), tp, sl,
                unFee, unNet, avg(unNet, selected.size()), pct(unNet, totalMargin), levFee, levNet, avg(levNet, selected.size()), pct(levNet, totalMargin));
    }

    private BigDecimal sum(List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP); }
    private BigDecimal avg(BigDecimal total, int count) { return count == 0 ? zero() : total.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP); }
    private BigDecimal pct(BigDecimal part, BigDecimal total) { return total == null || total.compareTo(BigDecimal.ZERO) == 0 ? zero() : part.multiply(BigDecimal.valueOf(100)).divide(total, SCALE, RoundingMode.HALF_UP); }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal zero() { return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP); }
    public boolean isValidClosedV20Trade(PaperPositionEntity position) {
        return position != null
                && "V20".equalsIgnoreCase(position.getStrategyVersion())
                && ("TAKE_PROFIT".equals(position.getExitReason()) || "STOP_LOSS".equals(position.getExitReason()))
                && position.getUnleveragedNetPnlUsdt() != null
                && position.getLeveragedNetPnlUsdt() != null
                && position.getUnleveragedTotalFeeUsdt() != null
                && position.getLeveragedTotalFeeUsdt() != null;
    }
}
