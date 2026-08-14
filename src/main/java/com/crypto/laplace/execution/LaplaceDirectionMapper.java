package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePaperVariant;
import com.crypto.laplace.model.LaplaceSignal;
import org.springframework.stereotype.Component;

@Component
public class LaplaceDirectionMapper {
    public PositionSide map(LaplacePaperVariant variant, LaplaceSignal raw) {
        return mapDirection(variant, raw);
    }

    public static PositionSide mapDirection(LaplacePaperVariant variant, LaplaceSignal raw) {
        if (raw == null || raw == LaplaceSignal.NONE) return null;
        boolean rawLong = raw == LaplaceSignal.LONG;
        boolean executeLong = variant.signalInverted() ? !rawLong : rawLong;
        return executeLong ? PositionSide.LONG : PositionSide.SHORT;
    }
}
