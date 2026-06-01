package org.pead.broker.model;

import java.math.BigDecimal;

public record ModifyOrderRequest(
        BigDecimal price,
        Integer quantity,
        BigDecimal stopLoss
) {
}
