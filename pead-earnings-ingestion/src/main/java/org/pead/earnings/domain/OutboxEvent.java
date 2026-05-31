package org.pead.earnings.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Transactional outbox pattern: events are written here atomically with the
 * domain entity update, then published to Kafka by OutboxPublisher.
 */
@Entity
@Table(name = "outbox_events",
    indexes = @Index(name = "idx_outbox_status", columnList = "status, created_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] payload;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";  // PENDING, PUBLISHED, FAILED

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
