package com.nikit.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Same algorithm as the Redis script, for tests (app.store=memory). Only limits a single instance. */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory")
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private static final class Bucket {
        double tokens;
        long timestampMillis;
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Decision tryAcquire(String key, int capacity, double refillPerSecond) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            Bucket b = new Bucket();
            b.tokens = capacity;
            b.timestampMillis = System.currentTimeMillis();
            return b;
        });
        synchronized (bucket) {
            long now = System.currentTimeMillis();
            bucket.tokens = Math.min(capacity, bucket.tokens + Math.max(0, now - bucket.timestampMillis) / 1000.0 * refillPerSecond);
            bucket.timestampMillis = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return new Decision(true, (long) Math.floor(bucket.tokens), 0);
            }
            long retry = (long) Math.ceil((1 - bucket.tokens) / refillPerSecond * 1000);
            return new Decision(false, 0, retry);
        }
    }
}
