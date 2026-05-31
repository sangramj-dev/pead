package org.pead.scanner.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.scanner.service.WatchlistService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateStreamProcessor {

    private final WatchlistService watchlistService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TopicNames.SCANNER_CANDIDATE,
            groupId = "pead-scanner-candidate-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCandidate(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        String ticker = record.key();
        try {
            CandidateSymbolEvent candidate = objectMapper.readValue(record.value(), CandidateSymbolEvent.class);
            watchlistService.addToWatchlist(candidate);
            ack.acknowledge();
            log.info("Processed candidate for ticker={} partition={} offset={}",
                    ticker, record.partition(), record.offset());
        } catch (Exception e) {
            log.error("Failed to process CandidateSymbolEvent for ticker={}: {}",
                    ticker, e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
