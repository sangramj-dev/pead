package org.pead.marketdata.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.PriceBarEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.marketdata.domain.DailyIndicator;
import org.pead.marketdata.domain.PriceBar;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceBarPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(PriceBar bar, DailyIndicator indicator, String correlationId) {
        PriceBarEvent event = PriceBarEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .ticker(bar.getTicker())
                .barDate(bar.getBarDate())
                .timeframe("D1")
                .open(bar.getOpenPrice().doubleValue())
                .high(bar.getHighPrice().doubleValue())
                .low(bar.getLowPrice().doubleValue())
                .close(bar.getClosePrice().doubleValue())
                .volume(bar.getVolume())
                .vwap(bar.getVwap() != null ? bar.getVwap().doubleValue() : null)
                .ema20(indicator != null && indicator.getEma20() != null ? indicator.getEma20().doubleValue() : null)
                .ema50(indicator != null && indicator.getEma50() != null ? indicator.getEma50().doubleValue() : null)
                .relativeVolume(indicator != null && indicator.getRelVolume() != null ? indicator.getRelVolume().doubleValue() : null)
                .correlationId(correlationId)
                .eventTimestamp(System.currentTimeMillis())
                .build();

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize PriceBarEvent", e);
        }

        kafkaTemplate.send(TopicNames.MARKET_PRICE_BAR, bar.getTicker(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PriceBarEvent for {}: {}", bar.getTicker(), ex.getMessage());
                    }
                });
    }
}
