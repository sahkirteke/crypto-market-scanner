package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.*;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.pool.*;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class StartupMarketUniverseServiceTest {
 @Test void laplaceUses25mForNewSymbolsAnd20mForExistingMembersWithoutChangingScanner30m(){
  BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplaceCoinPoolRepository pool=mock(LaplaceCoinPoolRepository.class);LaplacePaperPositionRepository positions=mock(LaplacePaperPositionRepository.class);LaplaceStrategyProperties props=new LaplaceStrategyProperties();
  when(client.getExchangeInfo()).thenReturn(List.of(symbol("NEW26"),symbol("NEW249"),symbol("MEMBER22"),symbol("PROTECTED19")));
  when(client.getAll24hTickers()).thenReturn(List.of(ticker("NEW26","26000000"),ticker("NEW249","24900000"),ticker("MEMBER22","22000000"),ticker("PROTECTED19","19000000")));
  when(pool.findAll()).thenReturn(List.of(member("MEMBER22",LaplaceCoinPoolState.ACTIVE),member("PROTECTED19",LaplaceCoinPoolState.PENDING_REMOVAL)));
  when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(LaplacePaperPositionEntity.builder().symbol("PROTECTED19").build()));
  StartupMarketUniverseService service=new StartupMarketUniverseService(client,props,pool,positions);service.initialize();
  assertThat(service.symbols()).contains("NEW26","MEMBER22","PROTECTED19").doesNotContain("NEW249");
  assertThat(service.minimumVolumeThreshold()).isEqualByComparingTo("25000000");
  assertThat(new ScannerProperties().getLiquidity().getMinQuoteVolume24h()).isEqualByComparingTo("30000000");
 }
 @Test void volumeThresholdDefaultsBindToLaplaceProperties(){LaplaceStrategyProperties p=new LaplaceStrategyProperties();assertThat(p.getLaplace().getVolumeScan().getEntryMinQuoteVolume()).isEqualByComparingTo("25000000");assertThat(p.getLaplace().getVolumeScan().getRetentionMinQuoteVolume()).isEqualByComparingTo("20000000");}
 @Test void applicationYamlBindsLaplaceThresholds() throws Exception {var loader=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();loader.setResources(new org.springframework.core.io.ClassPathResource("application.yml"));var source=new org.springframework.core.env.PropertiesPropertySource("yaml",Objects.requireNonNull(loader.getObject()));var env=new org.springframework.core.env.StandardEnvironment();env.getPropertySources().addFirst(source);LaplaceStrategyProperties p=org.springframework.boot.context.properties.bind.Binder.get(env).bind("trading",org.springframework.boot.context.properties.bind.Bindable.of(LaplaceStrategyProperties.class)).orElseThrow();assertThat(p.getLaplace().getVolumeScan().getEntryMinQuoteVolume()).isEqualByComparingTo("25000000");assertThat(p.getLaplace().getVolumeScan().getRetentionMinQuoteVolume()).isEqualByComparingTo("20000000");}
 private SymbolInfo symbol(String s){return SymbolInfo.builder().symbol(s).quoteAsset("USDT").contractType("PERPETUAL").status("TRADING").build();}
 private Ticker24h ticker(String s,String v){return Ticker24h.builder().symbol(s).quoteVolume(new BigDecimal(v)).build();}
 private LaplaceCoinPoolEntity member(String s,LaplaceCoinPoolState state){return LaplaceCoinPoolEntity.builder().symbol(s).state(state).build();}
}
