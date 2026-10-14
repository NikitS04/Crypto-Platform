package com.nikit.repository;

import com.nikit.modal.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    List<PortfolioSnapshot> findByUserIdOrderByTimestampAsc(Long userId);

    List<PortfolioSnapshot> findByUserIdAndTimestampAfterOrderByTimestampAsc(Long userId, Instant after);
}
