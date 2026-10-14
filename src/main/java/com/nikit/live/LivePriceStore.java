package com.nikit.live;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Holds the latest streamed price per symbol. Backed by Redis in production, a map in tests. */
public interface LivePriceStore {

    void putAll(Collection<LivePrice> prices);

    Optional<LivePrice> get(String symbol);

    Map<String, LivePrice> getAll();
}
