package com.nikit.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the latest price per symbol in a single Redis hash ("trading:live-prices", field = symbol).
 * Keeping it in Redis rather than in memory means every backend instance and any REST read sees the
 * same prices, and a restarted instance can serve prices immediately instead of waiting for the feed.
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "redis", matchIfMissing = true)
public class RedisLivePriceStore implements LivePriceStore {

    static final String KEY = "trading:live-prices";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLivePriceStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void putAll(Collection<LivePrice> prices) {
        if (prices.isEmpty()) {
            return;
        }
        Map<String, String> fields = new HashMap<>();
        for (LivePrice p : prices) {
            fields.put(p.symbol(), write(p));
        }
        redis.opsForHash().putAll(KEY, fields);
        // If the feed dies the whole hash expires rather than serving prices that look live but aren't
        redis.expire(KEY, Duration.ofMinutes(5));
    }

    @Override
    public Optional<LivePrice> get(String symbol) {
        Object value = redis.opsForHash().get(KEY, symbol.toLowerCase());
        return Optional.ofNullable(value).map(v -> read(v.toString()));
    }

    @Override
    public Map<String, LivePrice> getAll() {
        Map<String, LivePrice> result = new HashMap<>();
        redis.opsForHash().entries(KEY).forEach((k, v) -> result.put(k.toString(), read(v.toString())));
        return result;
    }

    private String write(LivePrice price) {
        try {
            return objectMapper.writeValueAsString(price);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private LivePrice read(String json) {
        try {
            return objectMapper.readValue(json, LivePrice.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
