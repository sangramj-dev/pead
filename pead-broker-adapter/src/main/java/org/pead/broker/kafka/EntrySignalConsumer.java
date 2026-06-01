package org.pead.broker.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.broker.model.OrderRequest;
import org.pead.broker.service.OrderExecutionService;
import org.pead.common.event.EntrySignalEvent;
import org.pead.common.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntrySignalConsumer {

    private final OrderExecutionService orderExecutionService;

    @KafkaListener(
            topics = TopicNames.SIGNAL_ENTRY,
            groupId = "broker-adapter-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(EntrySignalEvent event) {
        log.info("Received entry signal: {} {} x{} @ {} (signalId={}, peadScore={})",
                event.getDirection(), event.getTicker(), event.getQuantity(),
                event.getEntryPrice(), event.getSignalId(), event.getPeadScore());

        try {
            OrderRequest orderRequest = new OrderRequest(
                    event.getTicker(),
                    event.getDirection(),
                    event.getQuantity(),
                    event.getEntryPrice(),
                    event.getStopLoss(),
                    event.getTarget1(),
                    event.getTarget2(),
                    event.getSignalId(),
                    event.getPeadScore(),
                    "LIMIT"
            );

            orderExecutionService.executeOrder(orderRequest);
        } catch (Exception e) {
            log.error("Failed to process entry signal for {} (signalId={}): {}",
                    event.getTicker(), event.getSignalId(), e.getMessage(), e);
        }
    }
}
