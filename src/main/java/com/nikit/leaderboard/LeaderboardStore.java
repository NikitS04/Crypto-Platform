package com.nikit.leaderboard;

import java.util.List;
import java.util.Optional;

/** Users ranked by return. Backed by a Redis sorted set in production, a map in tests. */
public interface LeaderboardStore {

    record Entry(long userId, double returnPct) {
    }

    void update(long userId, double returnPct);

    /** Highest returns first. */
    List<Entry> top(int limit);

    /** 1-based rank, if the user is on the board. */
    Optional<Long> rank(long userId);
}
