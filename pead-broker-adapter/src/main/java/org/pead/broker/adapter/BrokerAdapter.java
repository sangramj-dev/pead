package org.pead.broker.adapter;

import org.pead.broker.model.*;

import java.util.List;

public interface BrokerAdapter {

    OrderResponse placeOrder(OrderRequest request);

    OrderResponse modifyOrder(String orderId, ModifyOrderRequest request);

    OrderResponse cancelOrder(String orderId);

    List<Position> getPositions();

    List<Order> getOrders();

    boolean isConnected();

    String getBrokerType();
}
