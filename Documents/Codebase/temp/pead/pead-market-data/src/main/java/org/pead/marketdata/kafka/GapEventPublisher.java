package org.pead.marketdata.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.domain.GapDirection;
import org.pead.common.event.GapDetectedEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.marketdata.domain.GapEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GapEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(GapEvent gapEvent, String correlationId) {
        GapDetectedEvent event = GapDetectedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ticker(gapEvent.getTicker())
                .tradeDate(gapEvent.getGapDate())
                .prevClose(gapEvent.getPrevClose().doubleValue())
                .openPrice(gapEvent.getOpenPrice().doubleValue())
                .gapPct(gapEvent.getGapPct().doubleValue())
                .gapDirection(GapDirection.valueOf(gapEvent.getGapDirection()))
                .relativeVolume(gapEvent.getRelVolume().doubleValue())
                .earningsRelated(Boolean.TRUE.equals(gapEvent.getEarningsRelated()))
                .earningsDate(gapEvent.getEarningsDate())
                .correlationId(correlationId)
                .eventTimestamp(System.currentTimeMillis())
                .build();

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize GapDetectedEvent", e);
        }

        kafkaTemplate.send(TopicNames.MARKET_GAP_DETECTED, gapEvent.getTicker(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish GapDetectedEvent for {}: {}", gapEvent.getTicker(), ex.getMessage());
                    } else {
                        log.debug("Published GapDetectedEvent for {} on {}", gapEvent.getTicker(), gapEvent.getGapDate());
                    }
                });
    }
}
