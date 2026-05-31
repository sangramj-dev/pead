package org.pead.earnings.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.util.DateUtils;
import org.pead.earnings.client.FmpEarningsClient;
import org.pead.earnings.client.PolygonEarningsClient;
import org.pead.earnings.client.dto.EarningsDto;
import org.pead.earnings.service.EarningsIngestionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled triggers for earnings data ingestion.
 * Pre-market scrape: 6:00 AM ET (for pre-market announcements)
 * Post-market scrape: 5:00 PM ET (for after-hours announcements)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EarningsIngestionScheduler {

    private final PolygonEarningsClient polygonClient;
    private final FmpEarningsClient fmpClient;
    private final EarningsIngestionService ingestionService;
    private final MeterRegistry meterRegistry;

    private Counter runsSuccess;
    private Counter runsFailed;
    private Counter recordsFetched;

    @PostConstruct
    void initMetrics() {
        runsSuccess = Counter.builder("pead_earnings_ingestion_runs_total")
                .tag("status", "SUCCESS")
                .description("Total successful earnings ingestion runs")
                .register(meterRegistry);
        runsFailed = Counter.builder("pead_earnings_ingestion_runs_total")
                .tag("status", "FAILURE")
                .register(meterRegistry);
        recordsFetched = Counter.builder("pead_earnings_ingestion_records_fetched_total")
                .description("Total earnings records fetched and persisted")
                .register(meterRegistry);
    }

    /**
     * Pre-market scrape: runs at 6:00 AM Eastern Time on weekdays.
     */
    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "America/New_York")
    public void preMarketScrape() {
        log.info("Starting pre-market earnings scrape for {}", LocalDate.now());
        runIngestion(LocalDate.now());
    }

    /**
     * Post-market scrape: runs at 5:00 PM Eastern Time on weekdays.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    public void postMarketScrape() {
        log.info("Starting post-market earnings scrape for {}", LocalDate.now());
        runIngestion(LocalDate.now());
    }

    /**
     * Public method for manual trigger via REST API or actuator.
     */
    public int runIngestion(LocalDate date) {
        List<EarningsDto> allEarnings = new ArrayList<>();

        // Try Polygon first
        try {
            List<EarningsDto> polygonEarnings = polygonClient.getEarningsCalendar(date);
            allEarnings.addAll(polygonEarnings);
            log.info("Polygon returned {} earnings records for {}", polygonEarnings.size(), date);
        } catch (Exception e) {
            log.error("Polygon earnings fetch failed for {}: {}", date, e.getMessage());
        }

        // FMP as fallback/supplement
        try {
            List<EarningsDto> fmpEarnings = fmpClient.getEarningsCalendar(date);
            // Add FMP records not already covered by Polygon
            for (EarningsDto fmpDto : fmpEarnings) {
                boolean alreadyHave = allEarnings.stream()
                        .anyMatch(e -> e.getTicker().equals(fmpDto.getTicker())
                                && e.getAnnouncementDate().equals(fmpDto.getAnnouncementDate()));
                if (!alreadyHave) allEarnings.add(fmpDto);
            }
            log.info("FMP returned {} earnings records for {}", fmpEarnings.size(), date);
        } catch (Exception e) {
            log.error("FMP earnings fetch failed for {}: {}", date, e.getMessage());
        }

        int ingested = 0;
        for (EarningsDto dto : allEarnings) {
            try {
                ingestionService.ingestEarnings(dto);
                ingested++;
                recordsFetched.increment();
            } catch (Exception e) {
                log.error("Failed to ingest earnings for {}: {}", dto.getTicker(), e.getMessage());
            }
        }

        if (ingested > 0) {
            runsSuccess.increment();
            log.info("Earnings ingestion complete for {}: {} records processed", date, ingested);
        } else {
            log.info("No earnings records ingested for {}", date);
        }

        return ingested;
    }
}
