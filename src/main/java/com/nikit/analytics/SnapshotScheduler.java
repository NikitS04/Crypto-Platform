package com.nikit.analytics;

import com.nikit.modal.Wallet;
import com.nikit.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-values every account so the equity curve and leaderboard track market moves. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.snapshots.scheduled", havingValue = "true", matchIfMissing = true)
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final WalletRepository walletRepository;
    private final PortfolioSnapshotService snapshotService;

    public SnapshotScheduler(WalletRepository walletRepository, PortfolioSnapshotService snapshotService) {
        this.walletRepository = walletRepository;
        this.snapshotService = snapshotService;
    }

    @Scheduled(fixedDelayString = "${app.snapshots.interval-ms:900000}", initialDelayString = "${app.snapshots.initial-delay-ms:60000}")
    public void snapshotAll() {
        int count = 0;
        for (Wallet wallet : walletRepository.findAll()) {
            try {
                snapshotService.record(wallet.getUser().getId());
                count++;
            } catch (Exception e) {
                log.warn("Scheduled snapshot failed for wallet {}: {}", wallet.getId(), e.getMessage());
            }
        }
        log.debug("Recorded {} scheduled portfolio snapshots", count);
    }
}
