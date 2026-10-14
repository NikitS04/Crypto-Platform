package com.nikit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.leaderboard.LeaderboardStore;
import com.nikit.leaderboard.RedisLeaderboardStore;
import com.nikit.live.LivePrice;
import com.nikit.live.RedisLivePriceStore;
import com.nikit.ratelimit.RateLimiter;
import com.nikit.ratelimit.RedisTokenBucketRateLimiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the Redis implementations against a real Redis on localhost:6379 (e.g. `docker compose up redis`).
 * Skipped automatically when Redis isn't running, so the rest of the suite needs no external services.
 */
class RedisBackedFeaturesTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        boolean available;
        try {
            factory.getConnection().ping();
            available = true;
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "Redis not running on localhost:6379");
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void tokenBucketIsAtomicUnderConcurrentRequests() throws Exception {
        RateLimiter limiter = new RedisTokenBucketRateLimiter(redis);
        String key = "trading:test:ratelimit:" + UUID.randomUUID();

        // 200 simultaneous requests against a bucket of 20 with negligible refill:
        // exactly 20 may pass. A non-atomic read-modify-write would let extras through.
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return limiter.tryAcquire(key, 20, 0.001).allowed();
            }));
        }
        go.countDown();
        int allowed = 0;
        for (Future<Boolean> f : results) {
            if (f.get(10, TimeUnit.SECONDS)) allowed++;
        }
        pool.shutdown();
        redis.delete(key);

        assertThat(allowed).isEqualTo(20);
    }

    @Test
    void tokenBucketRefillsOverTimeAndReportsRetryAfter() throws Exception {
        RateLimiter limiter = new RedisTokenBucketRateLimiter(redis);
        String key = "trading:test:ratelimit:" + UUID.randomUUID();

        assertThat(limiter.tryAcquire(key, 2, 10).allowed()).isTrue();
        assertThat(limiter.tryAcquire(key, 2, 10).allowed()).isTrue();
        RateLimiter.Decision denied = limiter.tryAcquire(key, 2, 10);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterMillis()).isBetween(1L, 100L);

        Thread.sleep(150); // 10 tokens/s -> at least one token back
        assertThat(limiter.tryAcquire(key, 2, 10).allowed()).isTrue();
        assertThat(redis.getExpire(key)).isPositive(); // idle buckets clean themselves up
        redis.delete(key);
    }

    @Test
    void leaderboardIsASortedSet() {
        // Use high ids unlikely to collide with real users, and clean up afterwards
        long a = 9_000_000_001L, b = 9_000_000_002L, c = 9_000_000_003L;
        LeaderboardStore board = new RedisLeaderboardStore(redis);
        try {
            board.update(a, 12.5);
            board.update(b, -3.0);
            board.update(c, 40.0);
            board.update(a, 15.0); // updating a score re-ranks, no duplicates

            List<LeaderboardStore.Entry> top = board.top(100).stream()
                    .filter(e -> e.userId() >= 9_000_000_000L).toList();
            assertThat(top).extracting(LeaderboardStore.Entry::userId).containsExactly(c, a, b);
            assertThat(top.get(1).returnPct()).isEqualTo(15.0);
            assertThat(board.rank(c).orElseThrow()).isLessThan(board.rank(a).orElseThrow());
            assertThat(board.rank(9_000_000_999L)).isEmpty();
        } finally {
            redis.opsForZSet().remove("trading:leaderboard", String.valueOf(a), String.valueOf(b), String.valueOf(c));
        }
    }

    @Test
    void livePricesRoundTripThroughARedisHash() {
        RedisLivePriceStore store = new RedisLivePriceStore(redis, new ObjectMapper());
        String symbol = "zz" + UUID.randomUUID().toString().substring(0, 6);
        try {
            store.putAll(List.of(new LivePrice(symbol, 1.2345, -2.5, 1700000000000L)));
            assertThat(store.get(symbol)).contains(new LivePrice(symbol, 1.2345, -2.5, 1700000000000L));
            assertThat(store.getAll()).containsKey(symbol);
            assertThat(redis.getExpire("trading:live-prices")).isPositive();
        } finally {
            redis.opsForHash().delete("trading:live-prices", symbol);
        }
    }
}
