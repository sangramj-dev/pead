package org.pead.marketdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.config.StrategyConfig;
import org.pead.common.util.FinancialMath;
import org.pead.marketdata.domain.GapEvent;
import org.pead.marketdata.domain.PriceBar;
import org.pead.marketdata.kafka.GapEventPublisher;
import org.pead.marketdata.repository.GapEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detects price gaps at market open.
 * A gap is defined as: abs((open - prevClose) / prevClose) > minGapPct
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GapDetectionService {

    private final GapEventRepository gapEventRepository;
    private final GapEventPublisher gapEventPublisher;
    private final StrategyConfig strategyConfig;

    @Transactional
    public GapEvent detectAndPublish(PriceBar today, PriceBar yesterday, double relVolume,
                                      boolean earningsRelated, LocalDate earningsDate) {
        if (yesterday == null) return null;

        double open = today.getOpenPrice().doubleValue();
        double prevClose = yesterday.getClosePrice().doubleValue();
        double gapPct = FinancialMath.gapPct(open, prevClose);
        double absGapPct = Math.abs(gapPct);

        if (absGapPct < strategyConfig.minGapPct()) {
            return null;  // Gap too small, not significant
        }

        // Idempotency check
        if (gapEventRepository.existsByTickerAndGapDate(today.getTicker(), today.getBarDate())) {
            return gapEventRepository.findByTickerAndGapDate(today.getTicker(), today.getBarDate()).orElse(null);
        }

        String direction = gapPct > 0 ? "UP" : "DOWN";
        GapEvent gapEvent = GapEvent.builder()
                .ticker(today.getTicker())
                .gapDate(today.getBarDate())
                .prevClose(yesterday.getClosePrice())
                .openPrice(today.getOpenPrice())
                .gapPct(BigDecimal.valueOf(gapPct).setScale(4, RoundingMode.HALF_UP))
                .gapDirection(direction)
                .relVolume(BigDecimal.valueOf(relVolume).setScale(4, RoundingMode.HALF_UP))
                .earningsRelated(earningsRelated)
                .earningsDate(earningsDate)
                .build();

        GapEvent saved = gapEventRepository.save(gapEvent);

        // Publish Kafka event
        gapEventPublisher.publish(saved, UUID.randomUUID().toString());

        log.info("Gap detected for {}: {}% {} (rel vol: {:.2f}x, earnings: {})",
                today.getTicker(), String.format("%.2f", gapPct), direction, relVolume, earningsRelated);

        return saved;
    }
}
