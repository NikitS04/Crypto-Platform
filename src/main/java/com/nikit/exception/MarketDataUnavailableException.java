package com.nikit.exception;

/** Thrown when the external market data provider is rate limiting us or unreachable. */
public class MarketDataUnavailableException extends RuntimeException {
    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
