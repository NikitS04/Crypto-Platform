package com.nikit.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Subscribes to Binance's public all-market mini-ticker stream, which pushes every symbol whose
 * price changed in the last second. USDT pairs are mapped to coin symbols ("BTCUSDT" -> "btc"),
 * written to the LivePriceStore and pushed to browsers.
 *
 * The connection is long-lived: if it drops (Binance also closes every stream after 24h) it
 * reconnects with exponential backoff capped at 30 seconds.
 */
@Component
@ConditionalOnProperty(name = "app.live-prices.enabled", havingValue = "true", matchIfMissing = true)
public class BinancePriceFeed {

    private static final Logger log = LoggerFactory.getLogger(BinancePriceFeed.class);
    private static final String QUOTE = "USDT";
    private static final long MAX_BACKOFF_SECONDS = 30;

    private final LivePriceStore store;
    private final LivePriceBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final URI streamUri;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService reconnector = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-feed-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;
    private volatile WebSocket socket;
    private long backoffSeconds = 1;

    public BinancePriceFeed(LivePriceStore store, LivePriceBroadcaster broadcaster, ObjectMapper objectMapper,
                            @Value("${app.live-prices.url:wss://stream.binance.com:9443/ws/!miniTicker@arr}") String url) {
        this.store = store;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
        this.streamUri = URI.create(url);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connect();
    }

    @PreDestroy
    public void stop() {
        running = false;
        reconnector.shutdownNow();
        if (socket != null) {
            socket.abort();
        }
    }

    private void connect() {
        if (!running) {
            return;
        }
        httpClient.newWebSocketBuilder()
                .buildAsync(streamUri, new Listener())
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        log.warn("Price feed connection failed: {}", error.getMessage());
                        scheduleReconnect();
                    } else {
                        socket = ws;
                        backoffSeconds = 1;
                        log.info("Connected to live price feed {}", streamUri);
                    }
                });
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        long delay = backoffSeconds;
        backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
        reconnector.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    /** Parses one mini-ticker array message into prices for USDT pairs. */
    static List<LivePrice> parse(JsonNode tickers) {
        List<LivePrice> prices = new ArrayList<>();
        for (JsonNode t : tickers) {
            String pair = t.path("s").asText();
            if (!pair.endsWith(QUOTE) || pair.length() == QUOTE.length()) {
                continue;
            }
            double close = t.path("c").asDouble();
            double open = t.path("o").asDouble();
            if (close <= 0) {
                continue;
            }
            double change = open > 0 ? (close - open) / open * 100 : 0;
            String symbol = pair.substring(0, pair.length() - QUOTE.length()).toLowerCase();
            prices.add(new LivePrice(symbol, close, change, t.path("E").asLong()));
        }
        return prices;
    }

    private void onMessage(String text) {
        try {
            List<LivePrice> prices = parse(objectMapper.readTree(text));
            store.putAll(prices);
            broadcaster.broadcast(prices);
        } catch (Exception e) {
            // A bad message or a Redis hiccup must not kill the feed thread
            log.warn("Failed to process price update: {}", e.getMessage());
        }
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                onMessage(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Price feed closed ({} {}), reconnecting", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Price feed error: {}", error.getMessage());
            scheduleReconnect();
        }
    }
}
