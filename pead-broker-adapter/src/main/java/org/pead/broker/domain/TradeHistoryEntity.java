package org.pead.broker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trade_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "trade_id")
    private UUID tradeId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "direction", nullable = false, length = 5)
    private String direction;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal exitPrice;

    @Column(name = "pnl", nullable = false, precision = 12, scale = 2)
    private BigDecimal pnl;

    @Column(name = "r_multiple", precision = 6, scale = 3)
    private BigDecimal rMultiple;

    @Column(name = "pead_score")
    private Integer peadScore;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "exit_date", nullable = false)
    private LocalDateTime exitDate;

    @Column(name = "exit_reason", length = 30)
    private String exitReason;

    @Column(name = "broker_type", length = 20)
    private String brokerType;
}
