package org.pead.broker.model;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId,
        String status,
        String brokerOrderId,
        String message,
        BigDecimal filledPrice,
        Integer filledQuantity
) {

    public static final String STATUS_PLACED = "PLACED";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
