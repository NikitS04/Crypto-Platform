package com.nikit.leaderboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis sorted set ("trading:leaderboard"): member = user id, score = return %.
 * Redis keeps the set ordered on every write, so ZADD, top-N (ZREVRANGE) and a user's rank
 * (ZREVRANK) are all O(log n) without ever sorting the whole user base.
 */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "redis", matchIfMissing = true)
public class RedisLeaderboardStore implements LeaderboardStore {

    static final String KEY = "trading:leaderboard";

    private final StringRedisTemplate redis;

    public RedisLeaderboardStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void update(long userId, double returnPct) {
        redis.opsForZSet().add(KEY, String.valueOf(userId), returnPct);
    }

    @Override
    public List<Entry> top(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(KEY, 0, limit - 1);
        List<Entry> entries = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                entries.add(new Entry(Long.parseLong(t.getValue()), t.getScore() == null ? 0 : t.getScore()));
            }
        }
        return entries;
    }

    @Override
    public Optional<Long> rank(long userId) {
        Long zeroBased = redis.opsForZSet().reverseRank(KEY, String.valueOf(userId));
        return Optional.ofNullable(zeroBased).map(r -> r + 1);
    }
}
