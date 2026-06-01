package org.pead.broker.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pead.broker.config.BrokerConfig;
import org.pead.broker.domain.BrokerOrderEntity;
import org.pead.broker.domain.PositionEntity;
import org.pead.broker.model.OrderRequest;
import org.pead.broker.model.OrderResponse;
import org.pead.broker.repository.BrokerOrderRepository;
import org.pead.broker.repository.PositionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperBrokerAdapterTest {

    @Mock
    private BrokerOrderRepository orderRepository;

    @Mock
    private PositionRepository positionRepository;

    private BrokerConfig brokerConfig;

    private PaperBrokerAdapter adapter;

    @BeforeEach
    void setUp() {
        brokerConfig = new BrokerConfig();
        brokerConfig.setType("paper");
        brokerConfig.setSlippagePct(new BigDecimal("0.001"));
        brokerConfig.setInitialCapital(new BigDecimal("1000000"));

        adapter = new PaperBrokerAdapter(orderRepository, positionRepository, brokerConfig);
    }

    @Test
    void placeOrder_shouldFillImmediatelyWithSlippage() {
        // Given
        OrderRequest request = new OrderRequest(
                "RELIANCE.NS",
                "LONG",
                10,
                new BigDecimal("2500.00"),
                new BigDecimal("2450.00"),
                new BigDecimal("2600.00"),
                new BigDecimal("2700.00"),
                "signal-123",
                75,
                "LIMIT"
        );

        UUID orderId = UUID.randomUUID();
        when(orderRepository.save(any(BrokerOrderEntity.class))).thenAnswer(invocation -> {
            BrokerOrderEntity entity = invocation.getArgument(0);
            entity.setOrderId(orderId);
            return entity;
        });

        // When
        OrderResponse response = adapter.placeOrder(request);

        // Then
        assertNotNull(response);
        assertEquals(OrderResponse.STATUS_FILLED, response.status());
        assertEquals(orderId.toString(), response.orderId());
        assertEquals(10, response.filledQuantity());

        // Verify slippage was applied (LONG = price + slippage)
        BigDecimal expectedSlippage = new BigDecimal("2500.00").multiply(new BigDecimal("0.001"));
        BigDecimal expectedFilledPrice = new BigDecimal("2500.00").add(expectedSlippage).setScale(2);
        assertEquals(expectedFilledPrice, response.filledPrice());

        // Verify order was saved
        ArgumentCaptor<BrokerOrderEntity> captor = ArgumentCaptor.forClass(BrokerOrderEntity.class);
        verify(orderRepository).save(captor.capture());
        BrokerOrderEntity savedOrder = captor.getValue();
        assertEquals("FILLED", savedOrder.getStatus());
        assertEquals("PAPER", savedOrder.getBrokerType());
        assertEquals("RELIANCE.NS", savedOrder.getTicker());
    }

    @Test
    void placeOrder_shortDirection_shouldSubtractSlippage() {
        // Given
        OrderRequest request = new OrderRequest(
                "INFY.NS",
                "SHORT",
                5,
                new BigDecimal("1500.00"),
                new BigDecimal("1550.00"),
                new BigDecimal("1400.00"),
                new BigDecimal("1350.00"),
                "signal-456",
                65,
                "LIMIT"
        );

        UUID orderId = UUID.randomUUID();
        when(orderRepository.save(any(BrokerOrderEntity.class))).thenAnswer(invocation -> {
            BrokerOrderEntity entity = invocation.getArgument(0);
            entity.setOrderId(orderId);
            return entity;
        });

        // When
        OrderResponse response = adapter.placeOrder(request);

        // Then
        // SHORT = price - slippage
        BigDecimal expectedSlippage = new BigDecimal("1500.00").multiply(new BigDecimal("0.001"));
        BigDecimal expectedFilledPrice = new BigDecimal("1500.00").subtract(expectedSlippage).setScale(2);
        assertEquals(expectedFilledPrice, response.filledPrice());
    }

    @Test
    void cancelOrder_shouldReturnCancelledStatus() {
        // Given
        UUID orderId = UUID.randomUUID();
        BrokerOrderEntity order = BrokerOrderEntity.builder()
                .orderId(orderId)
                .status("PENDING")
                .brokerOrderId("PAPER-abc123")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(BrokerOrderEntity.class))).thenReturn(order);

        // When
        OrderResponse response = adapter.cancelOrder(orderId.toString());

        // Then
        assertEquals(OrderResponse.STATUS_CANCELLED, response.status());
        assertEquals(orderId.toString(), response.orderId());
        verify(orderRepository).save(any(BrokerOrderEntity.class));
    }

    @Test
    void getPositions_shouldReturnOpenPositions() {
        // Given
        PositionEntity position1 = PositionEntity.builder()
                .positionId(UUID.randomUUID())
                .ticker("RELIANCE.NS")
                .direction("LONG")
                .quantity(10)
                .entryPrice(new BigDecimal("2500.00"))
                .currentPrice(new BigDecimal("2550.00"))
                .stopLoss(new BigDecimal("2450.00"))
                .target1(new BigDecimal("2600.00"))
                .target2(new BigDecimal("2700.00"))
                .unrealisedPnl(new BigDecimal("500.00"))
                .status("OPEN")
                .build();

        PositionEntity position2 = PositionEntity.builder()
                .positionId(UUID.randomUUID())
                .ticker("TCS.NS")
                .direction("LONG")
                .quantity(5)
                .entryPrice(new BigDecimal("3500.00"))
                .currentPrice(new BigDecimal("3480.00"))
                .stopLoss(new BigDecimal("3400.00"))
                .target1(new BigDecimal("3600.00"))
                .target2(new BigDecimal("3700.00"))
                .unrealisedPnl(new BigDecimal("-100.00"))
                .status("OPEN")
                .build();

        when(positionRepository.findByStatus("OPEN")).thenReturn(List.of(position1, position2));

        // When
        var positions = adapter.getPositions();

        // Then
        assertEquals(2, positions.size());
        assertEquals("RELIANCE.NS", positions.get(0).ticker());
        assertEquals("TCS.NS", positions.get(1).ticker());
        assertEquals("OPEN", positions.get(0).status());
    }

    @Test
    void isConnected_shouldAlwaysReturnTrue() {
        assertTrue(adapter.isConnected());
    }

    @Test
    void getBrokerType_shouldReturnPaper() {
        assertEquals("PAPER", adapter.getBrokerType());
    }
}
