package com.nikit.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PerformanceMetricsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");

    private static PerformanceMetrics.Point p(int day, double equity, double flows) {
        return new PerformanceMetrics.Point(T0.plus(day, ChronoUnit.DAYS), equity, flows);
    }

    @Test
    void depositsAreNotCountedAsReturns() {
        // Deposit 1000, gain 10%, deposit another 1000, flat
        List<PerformanceMetrics.Point> points = List.of(
                p(0, 1000, 1000),
                p(1, 1100, 1000),
                p(2, 2100, 2000),
                p(3, 2100, 2000));
        double[] index = PerformanceMetrics.twrIndex(points);
        assertThat(PerformanceMetrics.totalReturn(index)).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void timeWeightedReturnIgnoresHowMuchMoneyWasInvestedWhen() {
        // +10% on 100, then 10,000 is deposited and the account loses 10%: TWR is 1.1 * 0.9 - 1 = -1%,
        // even though the dollar loss is far bigger than the dollar gain
        List<PerformanceMetrics.Point> points = List.of(
                p(0, 100, 100),
                p(1, 110, 100),
                p(2, 10_110, 10_100),
                p(3, 9_099, 10_100));
        double[] index = PerformanceMetrics.twrIndex(points);
        assertThat(PerformanceMetrics.totalReturn(index)).isCloseTo(-0.01, within(1e-9));
    }

    @Test
    void withdrawalsAreNotCountedAsLosses() {
        List<PerformanceMetrics.Point> points = List.of(
                p(0, 1000, 1000),
                p(1, 500, 500));
        assertThat(PerformanceMetrics.totalReturn(PerformanceMetrics.twrIndex(points))).isCloseTo(0, within(1e-9));
    }

    @Test
    void emptyAccountsAreSkippedUntilFunded() {
        List<PerformanceMetrics.Point> points = List.of(
                p(0, 0, 0),
                p(1, 1000, 1000),
                p(2, 1200, 1000));
        assertThat(PerformanceMetrics.totalReturn(PerformanceMetrics.twrIndex(points))).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void maxDrawdownIsTheLargestPeakToTroughFall() {
        double[] index = {1.0, 1.2, 0.9, 1.1, 1.5, 1.2};
        // Peak 1.2 -> trough 0.9 is -25%; peak 1.5 -> 1.2 is only -20%
        assertThat(PerformanceMetrics.maxDrawdown(index)).isCloseTo(0.25, within(1e-9));
        assertThat(PerformanceMetrics.maxDrawdown(new double[]{1, 1.1, 1.2})).isZero();
    }

    @Test
    void sharpeAndVolatilityNeedEnoughHistory() {
        assertThat(PerformanceMetrics.sharpeRatio(List.of(0.01, 0.02))).isNull();
        assertThat(PerformanceMetrics.annualisedVolatility(List.of(0.01, 0.02))).isNull();
    }

    @Test
    void sharpeAndVolatilityAreAnnualisedOver365Days() {
        List<Double> daily = List.of(0.01, -0.005, 0.02, 0.0, 0.015, -0.01);
        double mean = daily.stream().mapToDouble(d -> d).average().orElseThrow();
        double sd = Math.sqrt(daily.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / (daily.size() - 1));

        assertThat(PerformanceMetrics.annualisedVolatility(daily)).isCloseTo(sd * Math.sqrt(365), within(1e-12));
        assertThat(PerformanceMetrics.sharpeRatio(daily)).isCloseTo(mean / sd * Math.sqrt(365), within(1e-12));
    }

    @Test
    void dailyReturnsUseTheLastValueOfEachDay() {
        List<PerformanceMetrics.Point> points = new ArrayList<>();
        points.add(new PerformanceMetrics.Point(T0, 100, 100));
        points.add(new PerformanceMetrics.Point(T0.plus(2, ChronoUnit.HOURS), 105, 100)); // same day, intraday move
        points.add(new PerformanceMetrics.Point(T0.plus(1, ChronoUnit.DAYS), 110, 100));
        double[] index = PerformanceMetrics.twrIndex(points);

        List<Double> daily = PerformanceMetrics.dailyReturns(points, index);
        assertThat(daily).hasSize(1);
        assertThat(daily.get(0)).isCloseTo(110.0 / 105.0 - 1, within(1e-12));
    }

    @Test
    void incrementalIndexMatchesFullReplay() {
        // Gains, a deposit, a loss and a withdrawal: extending the index one step at a time (as stored on each
        // snapshot) must land exactly where replaying the whole history does
        List<PerformanceMetrics.Point> points = List.of(
                p(0, 1000, 1000), p(1, 1100, 1000), p(2, 2150, 2000), p(3, 1900, 2000), p(4, 1500, 1600));
        double[] replay = PerformanceMetrics.twrIndex(points);

        double index = 1.0;
        for (int i = 1; i < points.size(); i++) {
            index = PerformanceMetrics.nextTwrIndex(index, points.get(i - 1), points.get(i));
            assertThat(index).isCloseTo(replay[i], within(1e-12));
        }
    }
}
