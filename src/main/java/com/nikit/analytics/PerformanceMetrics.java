package com.nikit.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure portfolio performance maths, kept free of Spring/JPA so it can be unit tested directly.
 */
public final class PerformanceMetrics {

    /** Crypto trades every day, so annualise over 365 days rather than 252 trading days. */
    static final double PERIODS_PER_YEAR = 365;

    /** Minimum daily returns before volatility/Sharpe are reported; fewer gives meaningless numbers. */
    static final int MIN_DAILY_RETURNS = 5;

    private PerformanceMetrics() {
    }

    /**
     * @param equity   account value at this time
     * @param netFlows cumulative deposits minus withdrawals (and transfers) up to this time
     */
    public record Point(Instant time, double equity, double netFlows) {
    }

    /**
     * Time-weighted return index, starting at 1.0. Between consecutive points the period return is
     *   r = (equity_now - flow_in_period) / equity_before - 1
     * so a deposit raises equity without counting as a gain, and the result doesn't depend on how
     * much money was in the account when (unlike a simple equity_end / equity_start).
     */
    public static double[] twrIndex(List<Point> points) {
        double[] index = new double[points.size()];
        double value = 1.0;
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                Point prev = points.get(i - 1);
                Point cur = points.get(i);
                double flow = cur.netFlows() - prev.netFlows();
                if (prev.equity() > 0) {
                    value *= (cur.equity() - flow) / prev.equity();
                }
            }
            index[i] = value;
        }
        return index;
    }

    public static double totalReturn(double[] index) {
        return index.length == 0 ? 0 : index[index.length - 1] - 1;
    }

    /** Largest peak-to-trough fall of the return index, as a positive fraction (0.25 = -25%). */
    public static double maxDrawdown(double[] index) {
        double peak = Double.NEGATIVE_INFINITY;
        double worst = 0;
        for (double v : index) {
            peak = Math.max(peak, v);
            if (peak > 0) {
                worst = Math.max(worst, (peak - v) / peak);
            }
        }
        return worst;
    }

    /** Daily returns from the last index value of each UTC day. */
    public static List<Double> dailyReturns(List<Point> points, double[] index) {
        Map<LocalDate, Double> closeByDay = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            closeByDay.put(LocalDate.ofInstant(points.get(i).time(), ZoneOffset.UTC), index[i]);
        }
        List<Double> closes = new ArrayList<>(closeByDay.values());
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            returns.add(closes.get(i) / closes.get(i - 1) - 1);
        }
        return returns;
    }

    /** Annualised volatility of daily returns, or null if there isn't enough history. */
    public static Double annualisedVolatility(List<Double> dailyReturns) {
        if (dailyReturns.size() < MIN_DAILY_RETURNS) {
            return null;
        }
        return stdDev(dailyReturns) * Math.sqrt(PERIODS_PER_YEAR);
    }

    /** Annualised Sharpe ratio with a zero risk-free rate, or null if there isn't enough history. */
    public static Double sharpeRatio(List<Double> dailyReturns) {
        if (dailyReturns.size() < MIN_DAILY_RETURNS) {
            return null;
        }
        double sd = stdDev(dailyReturns);
        if (sd == 0) {
            return null;
        }
        return mean(dailyReturns) / sd * Math.sqrt(PERIODS_PER_YEAR);
    }

    static double mean(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Sample standard deviation (n - 1). */
    static double stdDev(List<Double> xs) {
        if (xs.size() < 2) {
            return 0;
        }
        double m = mean(xs);
        double sumSq = 0;
        for (double x : xs) {
            sumSq += (x - m) * (x - m);
        }
        return Math.sqrt(sumSq / (xs.size() - 1));
    }
}
