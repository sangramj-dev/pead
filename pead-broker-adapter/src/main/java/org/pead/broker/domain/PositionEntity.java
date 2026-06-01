package org.pead.broker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "direction", nullable = false, length = 5)
    private String direction;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "stop_loss", precision = 12, scale = 2)
    private BigDecimal stopLoss;

    @Column(name = "target1", precision = 12, scale = 2)
    private BigDecimal target1;

    @Column(name = "target2", precision = 12, scale = 2)
    private BigDecimal target2;

    @Column(name = "target1_hit")
    @Builder.Default
    private boolean target1Hit = false;

    @Column(name = "unrealised_pnl", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal unrealisedPnl = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "opened_at")
    @Builder.Default
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "exit_price", precision = 12, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "exit_reason", length = 30)
    private String exitReason;

    @Column(name = "realised_pnl", precision = 12, scale = 2)
    private BigDecimal realisedPnl;
}
