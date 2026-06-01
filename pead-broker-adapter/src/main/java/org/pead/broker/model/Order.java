package org.pead.broker.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        String orderId,
        String ticker,
        String direction,
        int quantity,
        BigDecimal price,
        String status,
        String brokerOrderId,
        LocalDateTime createdAt
) {
}
