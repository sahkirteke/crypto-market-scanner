package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.StartupState;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceInvertedFalseTradeCoordinator {
    private final LaplaceInvertedFalsePositionRepository positions;
    private final LaplaceInvertedFalseExecutionService execution;
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final Map<String,LaplaceSignal> rawStates=new ConcurrentHashMap<>();
    public PositionSide mapRawSignalToExecutionSide(LaplaceSignal raw){return switch(raw){case LONG->PositionSide.LONG;case SHORT->PositionSide.SHORT;case NONE->null;};}
    public void initializeBaseline(String symbol,LaplaceSignal signal){rawStates.put(symbol,signal);}
    public void onSignal(LaplaceSignalResult signal,boolean inUniverse){
        if(!runtime.isActive()||signal==null||signal.startupState()!=StartupState.ACTIVE||signal.postStartupClosedBarCount()<1)return;
        LaplaceSignal previous=rawStates.put(signal.symbol(),signal.entrySignal());if(previous==null||previous==signal.entrySignal())return;
        PositionSide target=mapRawSignalToExecutionSide(signal.entrySignal());if(target==null)return;
        var open=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,signal.symbol(),LaplacePositionStatus.OPEN);
        if(open.isEmpty()){if(inUniverse&&signal.eligibleForExecution())execution.open(signal,target);return;}
        var current=open.getFirst();if(current.getSide()==target)return;
        if(mapRawSignalToExecutionSide(signal.strongReversalSignal())==target){execution.close(current,signal);if(inUniverse)execution.open(signal,target);}
    }
    public void clearRuntimeState(){rawStates.clear();}
}
