package org.pead.broker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.broker.adapter.BrokerAdapter;
import org.pead.broker.domain.BrokerOrderEntity;
import org.pead.broker.domain.PositionEntity;
import org.pead.broker.domain.TradeHistoryEntity;
import org.pead.broker.model.OrderRequest;
import org.pead.broker.model.OrderResponse;
import org.pead.broker.repository.BrokerOrderRepository;
import org.pead.broker.repository.PositionRepository;
import org.pead.broker.repository.TradeHistoryRepository;
import org.pead.common.kafka.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private final BrokerAdapter brokerAdapter;
    private final PositionRepository positionRepository;
    private final BrokerOrderRepository orderRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderResponse executeOrder(OrderRequest request) {
        log.info("Executing order: {} {} x{} @ {}",
                request.direction(), request.ticker(), request.quantity(), request.price());

        OrderResponse response = brokerAdapter.placeOrder(request);

        if (OrderResponse.STATUS_FILLED.equals(response.status())) {
            // Create position
            PositionEntity position = PositionEntity.builder()
                    .orderId(UUID.fromString(response.orderId()))
                    .ticker(request.ticker())
                    .direction(request.direction())
                    .quantity(request.quantity())
                    .entryPrice(response.filledPrice())
                    .currentPrice(response.filledPrice())
                    .stopLoss(request.stopLoss())
                    .target1(request.target1())
                    .target2(request.target2())
                    .build();

            PositionEntity savedPosition = positionRepository.save(position);
            log.info("Position opened: {} {} x{} @ {} (positionId={})",
                    request.direction(), request.ticker(), request.quantity(),
                    response.filledPrice(), savedPosition.getPositionId());

            // Publish trade-executed event to Kafka
            publishTradeExecutedEvent(request, response, savedPosition);
        } else {
            log.warn("Order not filled: {} - {}", response.status(), response.message());
        }

        return response;
    }

    @Transactional
    public void closePosition(UUID positionId, BigDecimal exitPrice, String exitReason) {
        log.info("Closing position {} at {} (reason: {})", positionId, exitPrice, exitReason);

        PositionEntity position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("Position not found: " + positionId));

        if (!"OPEN".equals(position.getStatus())) {
            log.warn("Position {} is already closed (status={})", positionId, position.getStatus());
            return;
        }

        // Calculate P&L
        BigDecimal pnl = calculatePnl(position, exitPrice);

        // Update position
        position.setStatus("CLOSED");
        position.setExitPrice(exitPrice);
        position.setExitReason(exitReason);
        position.setClosedAt(LocalDateTime.now());
        position.setRealisedPnl(pnl);
        positionRepository.save(position);

        // Calculate R-multiple (risk-adjusted return)
        BigDecimal rMultiple = calculateRMultiple(position, pnl);

        // Get the original order for pead_score and broker_type
        BrokerOrderEntity order = orderRepository.findById(position.getOrderId()).orElse(null);

        // Create trade history entry
        TradeHistoryEntity trade = TradeHistoryEntity.builder()
                .positionId(positionId)
                .ticker(position.getTicker())
                .direction(position.getDirection())
                .quantity(position.getQuantity())
                .entryPrice(position.getEntryPrice())
                .exitPrice(exitPrice)
                .pnl(pnl)
                .rMultiple(rMultiple)
                .peadScore(order != null ? order.getPeadScore() : null)
                .entryDate(position.getOpenedAt())
                .exitDate(LocalDateTime.now())
                .exitReason(exitReason)
                .brokerType(order != null ? order.getBrokerType() : null)
                .build();

        tradeHistoryRepository.save(trade);

        log.info("Position closed: {} {} P&L={} R={}",
                position.getTicker(), position.getDirection(), pnl, rMultiple);

        // Publish position-closed event
        publishPositionClosedEvent(position, exitPrice, pnl, exitReason);
    }

    private BigDecimal calculatePnl(PositionEntity position, BigDecimal exitPrice) {
        BigDecimal priceDiff = exitPrice.subtract(position.getEntryPrice());
        if ("SHORT".equalsIgnoreCase(position.getDirection())) {
            priceDiff = priceDiff.negate();
        }
        return priceDiff.multiply(BigDecimal.valueOf(position.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRMultiple(PositionEntity position, BigDecimal pnl) {
        if (position.getStopLoss() == null) {
            return null;
        }
        BigDecimal riskPerShare = position.getEntryPrice().subtract(position.getStopLoss()).abs();
        BigDecimal totalRisk = riskPerShare.multiply(BigDecimal.valueOf(position.getQuantity()));
        if (totalRisk.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return pnl.divide(totalRisk, 3, RoundingMode.HALF_UP);
    }

    private void publishTradeExecutedEvent(OrderRequest request, OrderResponse response, PositionEntity position) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventTimestamp", System.currentTimeMillis());
        event.put("ticker", request.ticker());
        event.put("signalId", request.signalId());
        event.put("direction", request.direction());
        event.put("quantity", request.quantity());
        event.put("filledPrice", response.filledPrice());
        event.put("orderId", response.orderId());
        event.put("positionId", position.getPositionId().toString());
        event.put("brokerType", brokerAdapter.getBrokerType());
        event.put("peadScore", request.peadScore());

        kafkaTemplate.send(TopicNames.TRADING_TRADE_EXECUTED, request.ticker(), event);
        log.info("Published trade-executed event for {}", request.ticker());
    }

    private void publishPositionClosedEvent(PositionEntity position, BigDecimal exitPrice,
                                            BigDecimal pnl, String exitReason) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventTimestamp", System.currentTimeMillis());
        event.put("positionId", position.getPositionId().toString());
        event.put("ticker", position.getTicker());
        event.put("direction", position.getDirection());
        event.put("entryPrice", position.getEntryPrice());
        event.put("exitPrice", exitPrice);
        event.put("pnl", pnl);
        event.put("exitReason", exitReason);

        kafkaTemplate.send(TopicNames.TRADING_POSITION_CLOSED, position.getTicker(), event);
        log.info("Published position-closed event for {}", position.getTicker());
    }
}
