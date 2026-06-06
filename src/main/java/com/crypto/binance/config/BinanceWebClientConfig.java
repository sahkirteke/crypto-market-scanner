package com.crypto.binance.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceWebClientConfig {

    @Bean
    @Qualifier("binanceWebClient")
    public WebClient binanceWebClient(BinanceProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(properties.getMaxInMemorySizeMb() * 1024 * 1024))
                .build();

        log.info("BINANCE_WEBCLIENT_CONFIG_READY baseUrl={} timeoutSeconds={} maxInMemorySizeMb={}",
                properties.getFuturesBaseUrl(), properties.getTimeoutSeconds(), properties.getMaxInMemorySizeMb());

        return WebClient.builder()
                .baseUrl(properties.getFuturesBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
