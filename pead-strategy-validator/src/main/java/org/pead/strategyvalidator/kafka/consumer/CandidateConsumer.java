package org.pead.strategyvalidator.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.strategyvalidator.service.StrategyValidationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateConsumer {

    private final StrategyValidationService strategyValidationService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            retryTopicSuffix = "-retry-",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{T(org.pead.common.kafka.TopicNames).SCANNER_CANDIDATE}",
            groupId = "#{T(org.pead.common.kafka.ConsumerGroupIds).STRATEGY_VALIDATOR}",
            containerFactory = "retryableKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        String ticker = record.key();
        log.debug("Received candidate: ticker={}, partition={}, offset={}",
                ticker, record.partition(), record.offset());

        CandidateSymbolEvent candidate;
        try {
            candidate = objectMapper.readValue(record.value(), CandidateSymbolEvent.class);
        } catch (IOException e) {
            log.error("Failed to deserialize CandidateSymbolEvent for ticker={}, discarding: {}",
                    ticker, e.getMessage());
            return;
        }

        strategyValidationService.validateCandidate(candidate);
    }
}
