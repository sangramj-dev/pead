package org.pead.broker.model;

import java.math.BigDecimal;

public record Position(
        String positionId,
        String ticker,
        String direction,
        int quantity,
        BigDecimal entryPrice,
        BigDecimal currentPrice,
        BigDecimal stopLoss,
        BigDecimal target1,
        BigDecimal target2,
        BigDecimal unrealisedPnl,
        String status
) {
}
