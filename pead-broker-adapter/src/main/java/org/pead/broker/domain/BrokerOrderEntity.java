package org.pead.broker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "broker_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "signal_id", length = 50)
    private String signalId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "direction", nullable = false, length = 5)
    private String direction;

    @Column(name = "order_type", nullable = false, length = 20)
    @Builder.Default
    private String orderType = "LIMIT";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stop_loss", precision = 12, scale = 2)
    private BigDecimal stopLoss;

    @Column(name = "target1", precision = 12, scale = 2)
    private BigDecimal target1;

    @Column(name = "target2", precision = 12, scale = 2)
    private BigDecimal target2;

    @Column(name = "filled_price", precision = 12, scale = 2)
    private BigDecimal filledPrice;

    @Column(name = "filled_quantity")
    private Integer filledQuantity;

    @Column(name = "broker_order_id", length = 50)
    private String brokerOrderId;

    @Column(name = "broker_type", nullable = false, length = 20)
    @Builder.Default
    private String brokerType = "PAPER";

    @Column(name = "pead_score")
    private Integer peadScore;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "filled_at")
    private LocalDateTime filledAt;
}
