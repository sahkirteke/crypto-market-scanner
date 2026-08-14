package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceSignalFanOutService {
    private final LaplacePaperTradeCoordinator invertedTrue;
    private final LaplaceInvertedFalseTradeCoordinator invertedFalse;
    private final com.crypto.laplace.service.LaplaceInvertedFalseDiagnosticLogService falseDiagnostics;
    public void baseline(String symbol,com.crypto.laplace.model.LaplaceSignal signal){invertedTrue.initializeBaseline(symbol,signal);invertedFalse.initializeBaseline(symbol,signal);}
    public void onSignal(LaplaceSignalResult signal,boolean inUniverse){
        try{invertedTrue.onSignal(signal,inUniverse);}catch(RuntimeException e){log.error("INVERTED_TRUE_SIGNAL_FAILED symbol={}",signal.symbol(),e);}
        try{falseDiagnostics.signal(signal);invertedFalse.onSignal(signal,inUniverse);}catch(RuntimeException e){log.error("INVERTED_FALSE_SIGNAL_FAILED symbol={}",signal.symbol(),e);}
    }
}
