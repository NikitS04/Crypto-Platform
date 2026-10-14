package com.nikit.analytics;

import com.nikit.domain.OrderStatus;
import com.nikit.domain.OrderType;
import com.nikit.modal.Asset;
import com.nikit.modal.PortfolioSnapshot;
import com.nikit.modal.Wallet;
import com.nikit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioAnalyticsService {

    private static final Duration CURVE_WINDOW = Duration.ofDays(90);

    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuation valuation;

    public PortfolioAnalyticsService(WalletRepository walletRepository, AssetRepository assetRepository,
                                     OrderRepository orderRepository, WalletTransactionRepository walletTransactionRepository,
                                     PortfolioSnapshotRepository snapshotRepository, PortfolioValuation valuation) {
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.valuation = valuation;
    }

    @Transactional(readOnly = true)
    public PortfolioAnalytics analyse(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);
        double cash = wallet == null || wallet.getBalance() == null ? 0 : wallet.getBalance().doubleValue();

        // Holdings, valued at live prices
        List<Asset> assets = assetRepository.findByUserId(userId);
        double holdingsValue = 0;
        double unrealized = 0;
        for (Asset a : assets) {
            holdingsValue += valuation.valueOf(a);
            unrealized += (valuation.priceOf(a.getCoin()) - a.getBuyPrice()) * a.getQuantity();
        }
        double equity = cash + holdingsValue;

        List<PortfolioAnalytics.Holding> holdings = new ArrayList<>();
        for (Asset a : assets) {
            double price = valuation.priceOf(a.getCoin());
            double value = price * a.getQuantity();
            double cost = a.getBuyPrice() * a.getQuantity();
            holdings.add(new PortfolioAnalytics.Holding(
                    a.getCoin().getId(), a.getCoin().getSymbol(), a.getCoin().getName(), a.getCoin().getImage(),
                    a.getQuantity(), a.getBuyPrice(), price, value, value - cost,
                    cost > 0 ? (value - cost) / cost * 100 : 0,
                    equity > 0 ? value / equity * 100 : 0));
        }

        // Realised P&L: for each completed sell, (sell price - average cost at the time) x quantity.
        // Summed in SQL; loading every order would also load each one's item in a separate query.
        double realized = orderRepository.sumRealizedPnl(userId, OrderType.SELL, OrderStatus.SUCCESS);

        // Performance from the stored snapshots plus a live "now" point
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByUserIdOrderByTimestampAsc(userId);
        List<PerformanceMetrics.Point> points = new ArrayList<>(PortfolioSnapshotService.toPoints(snapshots));
        double netFlows = walletTransactionRepository.sumAmountByUserAndTypes(userId, PortfolioSnapshotService.EXTERNAL_FLOWS);
        points.add(new PerformanceMetrics.Point(Instant.now(), equity, netFlows));

        double[] index = PerformanceMetrics.twrIndex(points);
        List<Double> daily = PerformanceMetrics.dailyReturns(points, index);
        Double vol = PerformanceMetrics.annualisedVolatility(daily);

        Instant curveStart = Instant.now().minus(CURVE_WINDOW);
        List<PortfolioAnalytics.EquityPoint> curve = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).time().isAfter(curveStart)) {
                curve.add(new PortfolioAnalytics.EquityPoint(points.get(i).time().toEpochMilli(),
                        round2(points.get(i).equity()), round2((index[i] - 1) * 100)));
            }
        }

        return new PortfolioAnalytics(
                round2(equity), round2(cash), round2(holdingsValue), round2(unrealized), round2(realized),
                round2(PerformanceMetrics.totalReturn(index) * 100),
                round2(PerformanceMetrics.maxDrawdown(index) * 100),
                vol == null ? null : round2(vol * 100),
                PerformanceMetrics.sharpeRatio(daily),
                daily.size(),
                holdings, curve);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
