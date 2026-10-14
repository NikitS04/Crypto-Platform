package com.nikit.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes price updates to browsers over a WebSocket at /ws/prices.
 *
 * A new client first gets a snapshot of every known price, then only the symbols that changed.
 * Each update is serialised once and fanned out to all sessions. Sessions are wrapped in
 * ConcurrentWebSocketSessionDecorator, which serialises concurrent sends and drops a client that
 * falls too far behind, so one slow browser can't block updates to everyone else.
 */
@Component
public class LivePriceBroadcaster extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LivePriceBroadcaster.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final LivePriceStore store;
    private final ObjectMapper objectMapper;

    public LivePriceBroadcaster(LivePriceStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        sessions.put(session.getId(), safe);
        send(safe, toMessage(store.getAll().values()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    public void broadcast(Collection<LivePrice> changed) {
        if (changed.isEmpty() || sessions.isEmpty()) {
            return;
        }
        TextMessage message = toMessage(changed);
        sessions.values().forEach(s -> send(s, message));
    }

    public int sessionCount() {
        return sessions.size();
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            sessions.remove(session.getId());
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping price subscriber {}: {}", session.getId(), e.getMessage());
            sessions.remove(session.getId());
        }
    }

    private TextMessage toMessage(Collection<LivePrice> prices) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(Map.of("type", "prices", "prices", prices)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
