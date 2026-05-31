package org.pead.backtesting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code backtests} table.
 */
@Entity
@Table(name = "backtests")
@Getter
@Setter
@NoArgsConstructor
public class BacktestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "backtest_id")
    private UUID backtestId;

    @Column(name = "parameters", columnDefinition = "jsonb", nullable = false)
    private String parameters;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "winning_trades")
    private Integer winningTrades;

    @Column(name = "losing_trades")
    private Integer losingTrades;

    @Column(name = "win_rate", precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 8, scale = 2)
    private BigDecimal profitFactor;

    @Column(name = "sharpe_ratio", precision = 6, scale = 3)
    private BigDecimal sharpeRatio;

    @Column(name = "max_drawdown_pct", precision = 6, scale = 3)
    private BigDecimal maxDrawdownPct;

    @Column(name = "total_pnl", precision = 15, scale = 2)
    private BigDecimal totalPnl;

    @Column(name = "final_equity", precision = 15, scale = 2)
    private BigDecimal finalEquity;

    @Column(name = "cagr", precision = 6, scale = 3)
    private BigDecimal cagr;

    @Column(name = "avg_r_multiple", precision = 6, scale = 3)
    private BigDecimal avgRMultiple;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
