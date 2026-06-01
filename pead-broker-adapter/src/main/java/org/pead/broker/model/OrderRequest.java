package org.pead.broker.model;

import java.math.BigDecimal;

public record OrderRequest(
        String ticker,
        String direction,
        int quantity,
        BigDecimal price,
        BigDecimal stopLoss,
        BigDecimal target1,
        BigDecimal target2,
        String signalId,
        int peadScore,
        String orderType
) {
}
