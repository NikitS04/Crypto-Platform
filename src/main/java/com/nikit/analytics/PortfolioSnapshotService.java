package com.nikit.analytics;

import com.nikit.domain.WalletTransactionType;
import com.nikit.leaderboard.LeaderboardStore;
import com.nikit.modal.Asset;
import com.nikit.modal.PortfolioSnapshot;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Records portfolio snapshots and keeps the leaderboard in sync.
 *
 * Snapshots are taken after every committed change to a user's cash or holdings (via
 * PortfolioChangedEvent) and periodically by SnapshotScheduler so the equity curve also moves
 * with market prices. Listening AFTER_COMMIT means a rolled-back trade never produces a snapshot,
 * and a failure here can never undo the trade itself.
 *
 * Event-driven snapshots run on a small background pool rather than the request thread. At
 * AFTER_COMMIT the request still holds its database connection, so taking a second one there
 * exhausts the connection pool under concurrent load (every thread holds one, waits for another).
 */
@Service
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);

    // Money entering or leaving the account; everything else (trades, price moves) is performance
    static final Set<WalletTransactionType> EXTERNAL_FLOWS = Set.of(
            WalletTransactionType.ADD_MONEY, WalletTransactionType.WITHDRAWAL, WalletTransactionType.WALLET_TRANSFER);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final OrderRepository orderRepository;
    private final LeaderboardStore leaderboardStore;
    private final PortfolioValuation valuation;
    private final TransactionTemplate newTransaction;
    private final ThreadPoolTaskExecutor executor;

    public PortfolioSnapshotService(UserRepository userRepository, WalletRepository walletRepository,
                                    AssetRepository assetRepository, WalletTransactionRepository walletTransactionRepository,
                                    PortfolioSnapshotRepository snapshotRepository, OrderRepository orderRepository,
                                    LeaderboardStore leaderboardStore, PortfolioValuation valuation,
                                    PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.orderRepository = orderRepository;
        this.leaderboardStore = leaderboardStore;
        this.valuation = valuation;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("portfolio-snapshot-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        // Under extreme load, dropping a snapshot is fine: the next trade or the scheduler takes another
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Snapshot queue full, skipping a snapshot"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPortfolioChanged(PortfolioChangedEvent event) {
        executor.execute(() -> {
            try {
                record(event.userId());
            } catch (Exception e) {
                log.warn("Could not snapshot portfolio for user {}: {}", event.userId(), e.getMessage());
            }
        });
    }

    public PortfolioSnapshot record(Long userId) {
        PortfolioSnapshot snapshot = newTransaction.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            Wallet wallet = walletRepository.findByUserId(userId);
            BigDecimal cash = wallet == null || wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();

            double holdings = 0;
            for (Asset asset : assetRepository.findByUserId(userId)) {
                holdings += valuation.valueOf(asset);
            }
            BigDecimal holdingsValue = BigDecimal.valueOf(holdings).setScale(2, RoundingMode.HALF_UP);

            PortfolioSnapshot s = new PortfolioSnapshot();
            s.setUser(user);
            s.setTimestamp(Instant.now());
            s.setCash(cash);
            s.setHoldingsValue(holdingsValue);
            s.setEquity(cash.add(holdingsValue));
            s.setNetFlows(BigDecimal.valueOf(walletTransactionRepository.sumAmountByUserAndTypes(userId, EXTERNAL_FLOWS)));
            return snapshotRepository.save(s);
        });
        updateLeaderboard(userId);
        return snapshot;
    }

    /** All-time time-weighted return in percent. */
    public double totalReturnPct(Long userId) {
        List<PerformanceMetrics.Point> points = toPoints(snapshotRepository.findByUserIdOrderByTimestampAsc(userId));
        return PerformanceMetrics.totalReturn(PerformanceMetrics.twrIndex(points)) * 100;
    }

    static List<PerformanceMetrics.Point> toPoints(List<PortfolioSnapshot> snapshots) {
        return snapshots.stream()
                .map(s -> new PerformanceMetrics.Point(s.getTimestamp(), s.getEquity().doubleValue(), s.getNetFlows().doubleValue()))
                .toList();
    }

    private void updateLeaderboard(Long userId) {
        // Only traders compete; an account that has only ever deposited cash would sit at 0%
        if (orderRepository.existsByUserId(userId)) {
            leaderboardStore.update(userId, totalReturnPct(userId));
        }
    }
}
