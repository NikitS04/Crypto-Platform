package com.nikit.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token bucket stored in Redis and updated by a Lua script.
 *
 * Redis runs a script atomically, so "read bucket, refill, take a token, write back" can't interleave
 * between concurrent requests (the same check-then-act race as the wallet double-spend, solved here
 * without locks). The script reads the clock with Redis TIME, so every backend instance agrees on time,
 * and sets an expiry so idle buckets disappear on their own. Because the state lives in Redis, the
 * limit holds across any number of backend instances.
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenBucketRateLimiter implements RateLimiter {

    static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local ts = tonumber(state[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            tokens = math.min(capacity, tokens + (math.max(0, now - ts) / 1000) * refill)
            local allowed = 0
            local retry_ms = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retry_ms = math.ceil((1 - tokens) / refill * 1000)
            end
            redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', now)
            redis.call('PEXPIRE', key, math.ceil(capacity / refill * 1000) + 1000)
            return {allowed, math.floor(tokens), retry_ms}
            """;

    private static final DefaultRedisScript<List> TOKEN_BUCKET = new DefaultRedisScript<>(SCRIPT, List.class);

    private final StringRedisTemplate redis;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decision tryAcquire(String key, int capacity, double refillPerSecond) {
        List<Long> result = redis.execute(TOKEN_BUCKET, List.of(key),
                String.valueOf(capacity), String.valueOf(refillPerSecond));
        return new Decision(result.get(0) == 1L, result.get(1), result.get(2));
    }
}
