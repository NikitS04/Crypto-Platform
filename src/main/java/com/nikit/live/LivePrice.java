package com.nikit.live;

/**
 * Latest streamed price for one coin.
 *
 * @param symbol             lower-case coin symbol, e.g. "btc" (matches Coin.symbol)
 * @param price              last traded price in USD (quoted against USDT on Binance)
 * @param changePercent24h   change over the rolling 24h window
 * @param timestamp          exchange event time, epoch millis
 */
public record LivePrice(String symbol, double price, double changePercent24h, long timestamp) {

    public boolean isFresh(long maxAgeMillis) {
        return System.currentTimeMillis() - timestamp <= maxAgeMillis;
    }
}
