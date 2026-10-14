package com.nikit.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LivePriceConfig implements WebSocketConfigurer {

    private final LivePriceBroadcaster broadcaster;
    private final String frontendUrl;

    public LivePriceConfig(LivePriceBroadcaster broadcaster, @Value("${app.frontend.url}") String frontendUrl) {
        this.broadcaster = broadcaster;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(broadcaster, "/ws/prices")
                .setAllowedOrigins(frontendUrl, "http://localhost:5173", "http://localhost:3000");
    }
}
