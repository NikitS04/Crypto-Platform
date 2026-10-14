package com.nikit.leaderboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance store used in tests (app.store=memory). */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory")
public class InMemoryLeaderboardStore implements LeaderboardStore {

    private final Map<Long, Double> scores = new ConcurrentHashMap<>();

    @Override
    public void update(long userId, double returnPct) {
        scores.put(userId, returnPct);
    }

    @Override
    public List<Entry> top(int limit) {
        return sorted().stream().limit(limit).toList();
    }

    @Override
    public Optional<Long> rank(long userId) {
        List<Entry> all = sorted();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).userId() == userId) {
                return Optional.of((long) i + 1);
            }
        }
        return Optional.empty();
    }

    private List<Entry> sorted() {
        return scores.entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Entry::returnPct).reversed())
                .toList();
    }
}
