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
@Table(name = "gap_events",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "gap_date"}, name = "uk_gap_ticker_date"),
    indexes = @Index(name = "idx_gap_ticker_date", columnList = "ticker, gap_date DESC"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "gap_date", nullable = false)
    private LocalDate gapDate;

    @Column(name = "prev_close", precision = 12, scale = 4)
    private BigDecimal prevClose;

    @Column(name = "open_price", precision = 12, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "gap_pct", precision = 8, scale = 4)
    private BigDecimal gapPct;

    @Column(name = "gap_direction", length = 10)
    private String gapDirection;  // UP or DOWN

    @Column(name = "rel_volume", precision = 8, scale = 4)
    private BigDecimal relVolume;

    @Column(name = "earnings_related")
    private Boolean earningsRelated;

    @Column(name = "earnings_date")
    private LocalDate earningsDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
