package com.nikit.ratelimit;

/** Token bucket rate limiter: each key holds up to {@code capacity} tokens, refilled continuously. */
public interface RateLimiter {

    record Decision(boolean allowed, long remaining, long retryAfterMillis) {
    }

    Decision tryAcquire(String key, int capacity, double refillPerSecond);
}
