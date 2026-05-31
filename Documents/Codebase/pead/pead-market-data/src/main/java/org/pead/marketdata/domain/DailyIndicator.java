package org.pead.marketdata.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_indicators",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"ticker", "indicator_date"},
        name = "uk_indicator_ticker_date"
    ),
    indexes = @Index(name = "idx_di_ticker_date", columnList = "ticker, indicator_date DESC"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "indicator_date", nullable = false)
    private LocalDate indicatorDate;

    @Column(name = "ema_20", precision = 12, scale = 4)
    private BigDecimal ema20;

    @Column(name = "ema_50", precision = 12, scale = 4)
    private BigDecimal ema50;

    @Column(name = "sma_200", precision = 12, scale = 4)
    private BigDecimal sma200;

    @Column(name = "atr_14", precision = 12, scale = 4)
    private BigDecimal atr14;

    @Column(name = "rel_volume", precision = 8, scale = 4)
    private BigDecimal relVolume;

    @Column(name = "avg_volume_20d")
    private Long avgVolume20d;

    @Column(name = "close_price", precision = 12, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "pct_from_high", precision = 8, scale = 4)
    private BigDecimal pctFromHigh;

    @Column(name = "above_ema20")
    private Boolean aboveEma20;

    @Column(name = "above_ema50")
    private Boolean aboveEma50;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        createdAt = Instant.now();
        if (closePrice != null && ema20 != null) {
            aboveEma20 = closePrice.compareTo(ema20) > 0;
        }
        if (closePrice != null && ema50 != null) {
            aboveEma50 = closePrice.compareTo(ema50) > 0;
        }
    }
}
