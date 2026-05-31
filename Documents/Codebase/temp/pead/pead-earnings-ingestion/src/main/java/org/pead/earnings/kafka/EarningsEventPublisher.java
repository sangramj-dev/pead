package org.pead.earnings.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.EarningsAnnouncementEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.earnings.domain.EarningsAnnouncement;
import org.pead.earnings.domain.OutboxEvent;
import org.pead.earnings.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EarningsEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishToOutbox(EarningsAnnouncement announcement, String correlationId) {
        EarningsAnnouncementEvent event = buildEvent(announcement, correlationId);

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EarningsAnnouncementEvent", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("EarningsAnnouncement")
                .aggregateId(announcement.getId().toString())
                .eventType("EarningsAnnouncementEvent")
                .topic(TopicNames.EARNINGS_ANNOUNCEMENT)
                .payload(payload)
                .partitionKey(announcement.getTicker())
                .status("PENDING")
                .build();

        outboxEventRepository.save(outboxEvent);
        log.debug("Written to outbox: EarningsAnnouncementEvent for {}", announcement.getTicker());
    }

    private EarningsAnnouncementEvent buildEvent(EarningsAnnouncement a, String correlationId) {
        return EarningsAnnouncementEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ticker(a.getTicker())
                .companyName("")
                .announcementDate(a.getAnnouncementDate())
                .announcementTime(a.getAnnouncementTime())
                .fiscalQuarter(a.getFiscalQuarter() != null ? a.getFiscalQuarter() : "")
                .fiscalYear(a.getFiscalYear() != null ? a.getFiscalYear() : 0)
                .epsActual(a.getEpsActual() != null ? a.getEpsActual().doubleValue() : null)
                .epsEstimate(a.getEpsEstimate() != null ? a.getEpsEstimate().doubleValue() : null)
                .epsSurprisePct(a.getEpsSurprisePct() != null ? a.getEpsSurprisePct().doubleValue() : null)
                .epsBeat(Boolean.TRUE.equals(a.getEpsBeat()))
                .revenueActual(a.getRevenueActual())
                .revenueEstimate(a.getRevenueEstimate())
                .revenueSurprisePct(a.getRevenueSurprisePct() != null ? a.getRevenueSurprisePct().doubleValue() : null)
                .revenueBeat(Boolean.TRUE.equals(a.getRevenueBeat()))
                .bothBeat(Boolean.TRUE.equals(a.getBothBeat()))
                .source("POLYGON")
                .correlationId(correlationId)
                .eventTimestamp(System.currentTimeMillis())
                .build();
    }
}
