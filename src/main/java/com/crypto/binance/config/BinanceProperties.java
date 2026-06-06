package com.crypto.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {
    private String futuresBaseUrl;
    private Integer timeoutSeconds;
}
