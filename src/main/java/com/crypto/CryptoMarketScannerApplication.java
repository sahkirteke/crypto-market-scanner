package com.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoMarketScannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoMarketScannerApplication.class, args);
	}

}
