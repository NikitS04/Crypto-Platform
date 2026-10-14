package com.nikit.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Connects a real WebSocket client to /ws/prices on a running server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LivePriceWebSocketTest {

    @LocalServerPort private int port;
    @Autowired private LivePriceStore store;
    @Autowired private LivePriceBroadcaster broadcaster;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void clientGetsASnapshotOnConnectThenStreamedUpdates() throws Exception {
        store.putAll(List.of(new LivePrice("btc", 80000, 1.5, System.currentTimeMillis())));

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/prices"), new WebSocket.Listener() {
                    private final StringBuilder buf = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            messages.add(buf.toString());
                            buf.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);

        JsonNode snapshot = objectMapper.readTree(messages.poll(5, TimeUnit.SECONDS));
        assertThat(snapshot.get("type").asText()).isEqualTo("prices");
        assertThat(snapshot.get("prices").toString()).contains("\"symbol\":\"btc\"").contains("80000");

        broadcaster.broadcast(List.of(new LivePrice("eth", 2500, -0.5, System.currentTimeMillis())));
        JsonNode update = objectMapper.readTree(messages.poll(5, TimeUnit.SECONDS));
        assertThat(update.get("prices")).hasSize(1);
        assertThat(update.get("prices").get(0).get("symbol").asText()).isEqualTo("eth");
        assertThat(update.get("prices").get(0).get("price").asDouble()).isEqualTo(2500);

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }
}
