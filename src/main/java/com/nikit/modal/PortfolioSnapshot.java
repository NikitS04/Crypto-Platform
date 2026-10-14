package com.nikit.modal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time valuation of one user's account. The series of snapshots drives the equity curve,
 * time-weighted return, drawdown and Sharpe ratio on the analytics page.
 */
@Entity
@Data
@Table(indexes = @Index(name = "idx_snapshot_user_time", columnList = "user_id, timestamp"))
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @JsonIgnore
    @ManyToOne
    private User user;

    private Instant timestamp;

    // cash + holdings value
    private BigDecimal equity;

    private BigDecimal cash;

    private BigDecimal holdingsValue;

    // Cumulative external cash flows (deposits, withdrawals, transfers) up to this point.
    // Equity changes caused by these are not investment returns, so TWR strips them out.
    private BigDecimal netFlows;

    // Time-weighted return index up to and including this snapshot (1.0 = break-even). Stored so the latest
    // return is one row read instead of a replay of the whole history. Null on rows written before it existed.
    private Double twrIndex;
}
