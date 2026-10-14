package com.nikit.analytics;

/** Published whenever a user's cash or holdings change (trade, deposit, withdrawal, transfer). */
public record PortfolioChangedEvent(Long userId) {
}
