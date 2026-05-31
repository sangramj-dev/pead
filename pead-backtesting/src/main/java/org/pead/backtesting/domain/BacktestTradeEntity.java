package org.pead.backtesting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code backtest_trades} table.
 */
@Entity
@Table(name = "backtest_trades")
@Getter
@Setter
@NoArgsConstructor
public class BacktestTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "backtest_id")
    private UUID backtestId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "direction", nullable = false, length = 5)
    private String direction;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 12, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "pnl", precision = 12, scale = 2)
    private BigDecimal pnl;

    @Column(name = "r_multiple", precision = 6, scale = 3)
    private BigDecimal rMultiple;

    @Column(name = "pead_score")
    private Integer peadScore;

    @Column(name = "exit_reason", length = 30)
    private String exitReason;
}
