package com.nikit.analytics;

import java.util.List;

/** Everything the analytics page shows, in one response. Percentages are 0-100 scale. */
public record PortfolioAnalytics(
        double equity,
        double cash,
        double holdingsValue,
        double unrealizedPnl,
        double realizedPnl,
        double totalReturnPct,
        double maxDrawdownPct,
        Double volatilityPct,
        Double sharpeRatio,
        int daysOfHistory,
        List<Holding> holdings,
        List<EquityPoint> equityCurve) {

    public record Holding(String coinId, String symbol, String name, String image, double quantity,
                          double avgCost, double price, double value, double unrealizedPnl,
                          double unrealizedPnlPct, double allocationPct) {
    }

    /** returnPct is the time-weighted return up to this point, so deposits don't show as gains. */
    public record EquityPoint(long timestamp, double equity, double returnPct) {
    }
}
