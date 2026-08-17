package com.crypto.laplace.service;

import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.scanner.config.ScannerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupMarketUniverseCooldownGateTest {
 @Test void cooldownRestartNeverTouchesBinance(){var client=mock(BinanceFuturesClient.class);var gate=mock(LaplaceMarketDataGate.class);var universe=new StartupMarketUniverseService(client,new ScannerProperties(),gate);universe.run(null);universe.initialize();verifyNoInteractions(client);}
 @Test void activeTrueInitializesFromBinance(){var client=mock(BinanceFuturesClient.class);var gate=mock(LaplaceMarketDataGate.class);when(gate.allowsMarketData()).thenReturn(true);when(client.getExchangeInfo()).thenReturn(List.of());when(client.getAll24hTickers()).thenReturn(List.of());new StartupMarketUniverseService(client,new ScannerProperties(),gate).initialize();verify(client).getExchangeInfo();verify(client).getAll24hTickers();}
}
