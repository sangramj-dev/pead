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
@Table(name = "price_bars",
    indexes = {
        @Index(name = "idx_pb_ticker_date", columnList = "ticker, bar_date DESC"),
        @Index(name = "idx_pb_date", columnList = "bar_date DESC")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "bar_date", nullable = false)
    private LocalDate barDate;

    @Column(length = 10, nullable = false)
    @Builder.Default
    private String timeframe = "1D";

    @Column(name = "open_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    @Column(precision = 12, scale = 4)
    private BigDecimal vwap;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
