package com.nikit.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-instance store used in tests (app.store=memory). */
@Component
@ConditionalOnProperty(name = "app.store", havingValue = "memory")
public class InMemoryLivePriceStore implements LivePriceStore {

    private final Map<String, LivePrice> prices = new ConcurrentHashMap<>();

    @Override
    public void putAll(Collection<LivePrice> batch) {
        batch.forEach(p -> prices.put(p.symbol(), p));
    }

    @Override
    public Optional<LivePrice> get(String symbol) {
        return Optional.ofNullable(prices.get(symbol.toLowerCase()));
    }

    @Override
    public Map<String, LivePrice> getAll() {
        return Map.copyOf(prices);
    }
}
