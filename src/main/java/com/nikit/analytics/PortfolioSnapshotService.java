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
import org.springframework.core.task.TaskRejectedException;
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
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * Requests are coalesced per user: while a snapshot for a user is queued, further trades don't queue
 * another, because the queued one will read the latest state anyway. Each snapshot stores the running
 * return index, so updating the leaderboard costs one row read rather than a replay of the user's whole
 * history (which made every snapshot slower than the last and let the queue overflow under load).
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

    // Users with a snapshot queued but not yet started
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    // Snapshots for the same user must not interleave, since each one extends the previous one's return index
    private final Object[] userLocks = new Object[64];

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
        // Coalescing means at most one queued task per user, so this only fills with 10k+ active users
        executor.setQueueCapacity(10_000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        for (int i = 0; i < userLocks.length; i++) {
            userLocks[i] = new Object();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPortfolioChanged(PortfolioChangedEvent event) {
        Long userId = event.userId();
        if (!pending.add(userId)) {
            return; // already queued; it will see this change
        }
        try {
            executor.execute(() -> {
                // Clear before reading state, so a trade committing during the snapshot queues a fresh one
                pending.remove(userId);
                try {
                    record(userId);
                } catch (Exception e) {
                    log.warn("Could not snapshot portfolio for user {}: {}", userId, e.getMessage());
                }
            });
        } catch (TaskRejectedException e) {
            pending.remove(userId);
            log.warn("Snapshot queue full, skipping a snapshot for user {}", userId);
        }
    }

    public PortfolioSnapshot record(Long userId) {
        synchronized (userLocks[Math.floorMod(userId.hashCode(), userLocks.length)]) {
            return recordLocked(userId);
        }
    }

    private PortfolioSnapshot recordLocked(Long userId) {
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
            s.setTwrIndex(nextIndex(userId, s));
            return snapshotRepository.save(s);
        });
        updateLeaderboard(userId, snapshot.getTwrIndex());
        return snapshot;
    }

    /** Return index after this snapshot, extending the user's latest one. */
    private double nextIndex(Long userId, PortfolioSnapshot current) {
        PortfolioSnapshot prev = snapshotRepository.findTopByUserIdOrderByTimestampDescIdDesc(userId);
        if (prev == null) {
            return 1.0;
        }
        double prevIndex;
        if (prev.getTwrIndex() != null) {
            prevIndex = prev.getTwrIndex();
        } else {
            // History written before the index was stored: replay it once; later snapshots build on this one
            double[] index = PerformanceMetrics.twrIndex(toPoints(snapshotRepository.findByUserIdOrderByTimestampAsc(userId)));
            prevIndex = index[index.length - 1];
        }
        return PerformanceMetrics.nextTwrIndex(prevIndex, toPoint(prev), toPoint(current));
    }

    static List<PerformanceMetrics.Point> toPoints(List<PortfolioSnapshot> snapshots) {
        return snapshots.stream().map(PortfolioSnapshotService::toPoint).toList();
    }

    static PerformanceMetrics.Point toPoint(PortfolioSnapshot s) {
        return new PerformanceMetrics.Point(s.getTimestamp(), s.getEquity().doubleValue(), s.getNetFlows().doubleValue());
    }

    private void updateLeaderboard(Long userId, double twrIndex) {
        // Only traders compete; an account that has only ever deposited cash would sit at 0%
        if (orderRepository.existsByUserId(userId)) {
            leaderboardStore.update(userId, (twrIndex - 1) * 100);
        }
    }
}
