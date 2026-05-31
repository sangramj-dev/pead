package org.pead.strategyvalidator.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.pead.common.domain.SignalStatus;
import org.pead.common.domain.TradeDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "validated_signals", indexes = {
        @Index(name = "idx_vs_ticker", columnList = "ticker"),
        @Index(name = "idx_vs_signal_date", columnList = "signal_date"),
        @Index(name = "idx_vs_status", columnList = "status")
})
@Data
public class ValidatedSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "signal_id")
    private UUID signalId;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "earnings_date")
    private LocalDate earningsDate;

    @Column(name = "pead_score", nullable = false)
    private int peadScore;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", nullable = false, precision = 12, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target_1", nullable = false, precision = 12, scale = 4)
    private BigDecimal target1;

    @Column(name = "target_2", nullable = false, precision = 12, scale = 4)
    private BigDecimal target2;

    @Column(name = "risk_reward_ratio", nullable = false, precision = 8, scale = 4)
    private BigDecimal riskRewardRatio;

    @Column(name = "eps_surprise_pct")
    private Double epsSurprisePct;

    @Column(name = "revenue_surprise_pct")
    private Double revenueSurprisePct;

    @Column(name = "gap_pct")
    private Double gapPct;

    @Column(name = "rel_volume")
    private Double relVolume;

    @Column(name = "ema_20", precision = 12, scale = 4)
    private BigDecimal ema20;

    @Column(name = "ema_50", precision = 12, scale = 4)
    private BigDecimal ema50;

    @Column(name = "above_ema20")
    private Boolean aboveEma20;

    @Column(name = "above_ema50")
    private Boolean aboveEma50;

    @Column(name = "close_near_high")
    private Boolean closeNearHigh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignalStatus status = SignalStatus.ACTIVE;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
