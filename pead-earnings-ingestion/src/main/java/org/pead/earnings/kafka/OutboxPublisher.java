package org.pead.earnings.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.earnings.domain.OutboxEvent;
import org.pead.earnings.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox_events table and publishes PENDING events to Kafka.
 * Runs every 500ms. Marks events as PUBLISHED after successful send.
 * This is the "relay" step of the transactional outbox pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private Counter publishedCounter;
    private Counter failedCounter;

    @PostConstruct
    void initMetrics() {
        publishedCounter = Counter.builder("pead_outbox_published_total")
                .description("Total outbox events successfully published to Kafka")
                .register(meterRegistry);
        failedCounter = Counter.builder("pead_outbox_failed_total")
                .description("Total outbox events that failed to publish")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingEvents.isEmpty()) return;

        log.debug("Publishing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                outboxEventRepository.markFailed(event.getId(), ex.getMessage());
                                failedCounter.increment();
                                log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
                            } else {
                                outboxEventRepository.markPublished(event.getId());
                                publishedCounter.increment();
                                log.debug("Published outbox event {} to {}", event.getId(), event.getTopic());
                            }
                        });
            } catch (Exception e) {
                outboxEventRepository.markFailed(event.getId(), e.getMessage());
                failedCounter.increment();
                log.error("Exception publishing outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
