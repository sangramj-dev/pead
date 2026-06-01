package org.pead.broker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "portfolio_equity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioEquityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "equity_date", nullable = false, unique = true)
    private LocalDate equityDate;

    @Column(name = "total_equity", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalEquity;

    @Column(name = "cash", nullable = false, precision = 15, scale = 2)
    private BigDecimal cash;

    @Column(name = "positions_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal positionsValue;

    @Column(name = "daily_pnl", precision = 12, scale = 2)
    private BigDecimal dailyPnl;

    @Column(name = "drawdown_pct", precision = 6, scale = 3)
    private BigDecimal drawdownPct;
}
