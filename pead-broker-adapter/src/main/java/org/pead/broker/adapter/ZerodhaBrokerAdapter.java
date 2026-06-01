package org.pead.broker.adapter;

import lombok.extern.slf4j.Slf4j;
import org.pead.broker.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "pead.broker.type", havingValue = "zerodha")
public class ZerodhaBrokerAdapter implements BrokerAdapter {

    @Override
    public OrderResponse placeOrder(OrderRequest request) {
        log.warn("Zerodha not yet configured - cannot place order for {}", request.ticker());
        return new OrderResponse(
                null,
                OrderResponse.STATUS_REJECTED,
                null,
                "Zerodha broker not yet configured",
                null,
                null
        );
    }

    @Override
    public OrderResponse modifyOrder(String orderId, ModifyOrderRequest request) {
        log.warn("Zerodha not yet configured - cannot modify order {}", orderId);
        return new OrderResponse(
                orderId,
                OrderResponse.STATUS_REJECTED,
                null,
                "Zerodha broker not yet configured",
                null,
                null
        );
    }

    @Override
    public OrderResponse cancelOrder(String orderId) {
        log.warn("Zerodha not yet configured - cannot cancel order {}", orderId);
        return new OrderResponse(
                orderId,
                OrderResponse.STATUS_REJECTED,
                null,
                "Zerodha broker not yet configured",
                null,
                null
        );
    }

    @Override
    public List<Position> getPositions() {
        log.warn("Zerodha not yet configured - returning empty positions");
        return Collections.emptyList();
    }

    @Override
    public List<Order> getOrders() {
        log.warn("Zerodha not yet configured - returning empty orders");
        return Collections.emptyList();
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public String getBrokerType() {
        return "ZERODHA";
    }
}
