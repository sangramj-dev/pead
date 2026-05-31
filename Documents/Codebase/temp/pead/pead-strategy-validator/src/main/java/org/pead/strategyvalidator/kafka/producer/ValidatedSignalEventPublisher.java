package org.pead.strategyvalidator.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.ValidatedSignalEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.strategyvalidator.domain.ValidatedSignal;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidatedSignalEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ValidatedSignal signal) {
        ValidatedSignalEvent event = buildEvent(signal);

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ValidatedSignalEvent for ticker " + signal.getTicker(), e);
        }

        kafkaTemplate.send(TopicNames.STRATEGY_VALIDATED_SIGNAL, signal.getTicker(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ValidatedSignalEvent for ticker={}: {}",
                                signal.getTicker(), ex.getMessage(), ex);
                    } else {
                        log.debug("Published ValidatedSignalEvent: ticker={}, partition={}, offset={}",
                                signal.getTicker(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private ValidatedSignalEvent buildEvent(ValidatedSignal s) {
        return ValidatedSignalEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(s.getCorrelationId() != null ? s.getCorrelationId() : "")
                .eventTimestamp(Instant.now().toEpochMilli())
                .signalId(s.getSignalId().toString())
                .ticker(s.getTicker())
                .direction(s.getDirection())
                .signalDate(s.getSignalDate())
                .earningsDate(s.getEarningsDate() != null ? s.getEarningsDate() : s.getSignalDate())
                .peadScore(s.getPeadScore())
                .entryPrice(s.getEntryPrice().doubleValue())
                .stopLoss(s.getStopLoss().doubleValue())
                .target1(s.getTarget1().doubleValue())
                .target2(s.getTarget2().doubleValue())
                .riskRewardRatio(s.getRiskRewardRatio().doubleValue())
                .epsSurprisePct(s.getEpsSurprisePct() != null ? s.getEpsSurprisePct() : 0.0)
                .revenueSurprisePct(s.getRevenueSurprisePct() != null ? s.getRevenueSurprisePct() : 0.0)
                .gapPct(s.getGapPct() != null ? s.getGapPct() : 0.0)
                .relativeVolume(s.getRelVolume() != null ? s.getRelVolume() : 0.0)
                .ema20(s.getEma20() != null ? s.getEma20().doubleValue() : 0.0)
                .ema50(s.getEma50() != null ? s.getEma50().doubleValue() : 0.0)
                .aboveEma20(Boolean.TRUE.equals(s.getAboveEma20()))
                .aboveEma50(Boolean.TRUE.equals(s.getAboveEma50()))
                .closeNearHigh(Boolean.TRUE.equals(s.getCloseNearHigh()))
                .expiryDate(s.getExpiryDate() != null ? s.getExpiryDate() : s.getSignalDate().plusDays(2))
                .build();
    }
}
