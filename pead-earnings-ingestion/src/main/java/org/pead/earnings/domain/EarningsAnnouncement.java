package org.pead.earnings.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pead.common.domain.AnnouncementTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "earnings_announcements",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"ticker", "announcement_date", "fiscal_quarter"},
        name = "uk_earnings_ticker_date_quarter"
    ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "announcement_time", length = 20)
    private AnnouncementTime announcementTime;

    @Column(name = "fiscal_quarter", length = 10)
    private String fiscalQuarter;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "eps_actual", precision = 10, scale = 4)
    private BigDecimal epsActual;

    @Column(name = "eps_estimate", precision = 10, scale = 4)
    private BigDecimal epsEstimate;

    @Column(name = "eps_surprise_pct", precision = 8, scale = 4)
    private BigDecimal epsSurprisePct;

    @Column(name = "eps_beat")
    private Boolean epsBeat;

    @Column(name = "revenue_actual")
    private Long revenueActual;

    @Column(name = "revenue_estimate")
    private Long revenueEstimate;

    @Column(name = "revenue_surprise_pct", precision = 8, scale = 4)
    private BigDecimal revenueSurprisePct;

    @Column(name = "revenue_beat")
    private Boolean revenueBeat;

    @Column(name = "both_beat")
    private Boolean bothBeat;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        bothBeat = Boolean.TRUE.equals(epsBeat) && Boolean.TRUE.equals(revenueBeat);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        bothBeat = Boolean.TRUE.equals(epsBeat) && Boolean.TRUE.equals(revenueBeat);
    }
}
