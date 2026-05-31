package org.pead.marketdata.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.client.PolygonRestClient;
import org.pead.marketdata.client.dto.OhlcvDto;
import org.pead.marketdata.domain.DailyIndicator;
import org.pead.marketdata.domain.PriceBar;
import org.pead.marketdata.kafka.PriceBarPublisher;
import org.pead.marketdata.repository.PriceBarRepository;
import org.pead.marketdata.service.GapDetectionService;
import org.pead.marketdata.service.TechnicalIndicatorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataScheduler {

    private final PolygonRestClient polygonClient;
    private final PriceBarRepository priceBarRepository;
    private final TechnicalIndicatorService indicatorService;
    private final GapDetectionService gapDetectionService;
    private final PriceBarPublisher priceBarPublisher;

    /**
     * EOD data pull: runs at 6:30 PM ET on weekdays (after market close + 2.5h for data propagation).
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "America/New_York")
    @Transactional
    public void eodDataPull() {
        LocalDate today = LocalDate.now();
        log.info("Starting EOD market data pull for {}", today);
        // In Phase 1, this would iterate over the watchlist tickers
        // For now: process a sample set until scanner provides watchlist
        log.info("EOD data pull complete for {}", today);
    }

    /**
     * Backfill historical data for a single ticker.
     * Called from REST API for manual backfill.
     */
    @Transactional
    public int backfillTicker(String ticker, LocalDate from, LocalDate to) {
        log.info("Backfilling {} from {} to {}", ticker, from, to);
        List<OhlcvDto> bars = polygonClient.getDailyBars(ticker, from, to);
        int processed = 0;

        for (int i = 0; i < bars.size(); i++) {
            OhlcvDto dto = bars.get(i);
            LocalDate barDate = Instant.ofEpochMilli(dto.getTimestamp()).atZone(ZoneOffset.UTC).toLocalDate();

            if (priceBarRepository.existsByTickerAndBarDateAndTimeframe(ticker, barDate, "1D")) {
                continue;
            }

            PriceBar priceBar = PriceBar.builder()
                    .ticker(ticker)
                    .barDate(barDate)
                    .timeframe("1D")
                    .openPrice(dto.getOpen())
                    .highPrice(dto.getHigh())
                    .lowPrice(dto.getLow())
                    .closePrice(dto.getClose())
                    .volume(dto.getVolume())
                    .vwap(dto.getVwap())
                    .build();

            priceBarRepository.save(priceBar);
            processed++;

            // Compute indicators after saving (needs historical data)
            DailyIndicator indicator = indicatorService.computeAndSave(ticker, barDate);

            // Detect gap
            PriceBar prevBar = i > 0 ? priceBarRepository.findByTickerAndBarDateAndTimeframe(
                    ticker,
                    Instant.ofEpochMilli(bars.get(i-1).getTimestamp()).atZone(ZoneOffset.UTC).toLocalDate(),
                    "1D"
            ).orElse(null) : null;

            if (prevBar != null && indicator != null) {
                double relVol = indicator.getRelVolume() != null ? indicator.getRelVolume().doubleValue() : 0.0;
                gapDetectionService.detectAndPublish(priceBar, prevBar, relVol, false, null);
            }

            // Publish price bar event
            priceBarPublisher.publish(priceBar, indicator, UUID.randomUUID().toString());
        }

        log.info("Backfill complete for {}: {} bars processed", ticker, processed);
        return processed;
    }
}
