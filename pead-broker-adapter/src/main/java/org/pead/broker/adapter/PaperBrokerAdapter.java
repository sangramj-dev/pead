package org.pead.broker.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.broker.config.BrokerConfig;
import org.pead.broker.domain.BrokerOrderEntity;
import org.pead.broker.model.*;
import org.pead.broker.model.Order;
import org.pead.broker.model.Position;
import org.pead.broker.repository.BrokerOrderRepository;
import org.pead.broker.repository.PositionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pead.broker.type", havingValue = "paper", matchIfMissing = true)
public class PaperBrokerAdapter implements BrokerAdapter {

    private final BrokerOrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final BrokerConfig brokerConfig;

    @Override
    public OrderResponse placeOrder(OrderRequest request) {
        log.info("PAPER BROKER: Placing order for {} {} x{} @ {}",
                request.direction(), request.ticker(), request.quantity(), request.price());

        // Calculate fill price with slippage
        BigDecimal slippage = request.price().multiply(brokerConfig.getSlippagePct());
        BigDecimal filledPrice = "LONG".equalsIgnoreCase(request.direction())
                ? request.price().add(slippage)
                : request.price().subtract(slippage);
        filledPrice = filledPrice.setScale(2, RoundingMode.HALF_UP);

        // Create and save order entity
        BrokerOrderEntity order = BrokerOrderEntity.builder()
                .signalId(request.signalId())
                .ticker(request.ticker())
                .direction(request.direction())
                .orderType(request.orderType() != null ? request.orderType() : "LIMIT")
                .status("FILLED")
                .quantity(request.quantity())
                .price(request.price())
                .stopLoss(request.stopLoss())
                .target1(request.target1())
                .target2(request.target2())
                .filledPrice(filledPrice)
                .filledQuantity(request.quantity())
                .brokerOrderId("PAPER-" + UUID.randomUUID().toString().substring(0, 8))
                .brokerType("PAPER")
                .peadScore(request.peadScore())
                .filledAt(LocalDateTime.now())
                .build();

        BrokerOrderEntity saved = orderRepository.save(order);

        log.info("PAPER BROKER: Order filled - {} {} x{} @ {} (slippage applied: {})",
                request.direction(), request.ticker(), request.quantity(), filledPrice, slippage);

        return new OrderResponse(
                saved.getOrderId().toString(),
                OrderResponse.STATUS_FILLED,
                saved.getBrokerOrderId(),
                "Paper order filled immediately",
                filledPrice,
                request.quantity()
        );
    }

    @Override
    public OrderResponse modifyOrder(String orderId, ModifyOrderRequest request) {
        log.info("PAPER BROKER: Modifying order {}", orderId);

        UUID id = UUID.fromString(orderId);
        BrokerOrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (request.price() != null) {
            order.setPrice(request.price());
        }
        if (request.quantity() != null) {
            order.setQuantity(request.quantity());
        }
        if (request.stopLoss() != null) {
            order.setStopLoss(request.stopLoss());
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        return new OrderResponse(
                orderId,
                order.getStatus(),
                order.getBrokerOrderId(),
                "Order modified successfully",
                order.getFilledPrice(),
                order.getFilledQuantity()
        );
    }

    @Override
    public OrderResponse cancelOrder(String orderId) {
        log.info("PAPER BROKER: Cancelling order {}", orderId);

        UUID id = UUID.fromString(orderId);
        BrokerOrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        return new OrderResponse(
                orderId,
                OrderResponse.STATUS_CANCELLED,
                order.getBrokerOrderId(),
                "Order cancelled",
                null,
                null
        );
    }

    @Override
    public List<Position> getPositions() {
        return positionRepository.findByStatus("OPEN").stream()
                .map(entity -> new Position(
                        entity.getPositionId().toString(),
                        entity.getTicker(),
                        entity.getDirection(),
                        entity.getQuantity(),
                        entity.getEntryPrice(),
                        entity.getCurrentPrice(),
                        entity.getStopLoss(),
                        entity.getTarget1(),
                        entity.getTarget2(),
                        entity.getUnrealisedPnl(),
                        entity.getStatus()
                ))
                .toList();
    }

    @Override
    public List<Order> getOrders() {
        return orderRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(entity -> new Order(
                        entity.getOrderId().toString(),
                        entity.getTicker(),
                        entity.getDirection(),
                        entity.getQuantity(),
                        entity.getPrice(),
                        entity.getStatus(),
                        entity.getBrokerOrderId(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String getBrokerType() {
        return "PAPER";
    }
}
