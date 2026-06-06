package com.crypto.binance.client;

public class BinanceClientException extends RuntimeException {

    public BinanceClientException(String message) {
        super(message);
    }

    public BinanceClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
