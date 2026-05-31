package org.pead.strategyvalidator.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rejected_candidates", indexes = {
        @Index(name = "idx_rc_ticker", columnList = "ticker"),
        @Index(name = "idx_rc_scan_date", columnList = "scan_date")
})
@Data
public class RejectedCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "scan_date", nullable = false)
    private LocalDate scanDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rejection_reasons", columnDefinition = "jsonb")
    private String rejectionReasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_metrics", columnDefinition = "jsonb")
    private String rawMetrics;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
