package org.pead.backtesting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code backtest_equity_curve} table.
 * Uses composite primary key (backtest_id, curve_date).
 */
@Entity
@Table(name = "backtest_equity_curve")
@IdClass(BacktestEquityCurveId.class)
@Getter
@Setter
@NoArgsConstructor
public class BacktestEquityCurveEntity {

    @Id
    @Column(name = "backtest_id")
    private UUID backtestId;

    @Id
    @Column(name = "curve_date")
    private LocalDate curveDate;

    @Column(name = "equity", nullable = false, precision = 15, scale = 2)
    private BigDecimal equity;

    @Column(name = "drawdown_pct", precision = 6, scale = 3)
    private BigDecimal drawdownPct;

    @Column(name = "open_positions")
    private Integer openPositions;
}
