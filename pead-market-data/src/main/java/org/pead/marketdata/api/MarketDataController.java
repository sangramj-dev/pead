package org.pead.marketdata.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.domain.DailyIndicator;
import org.pead.marketdata.domain.PriceBar;
import org.pead.marketdata.repository.DailyIndicatorRepository;
import org.pead.marketdata.repository.PriceBarRepository;
import org.pead.marketdata.scheduler.MarketDataScheduler;
import org.pead.marketdata.service.DataBackfillService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Slf4j
public class MarketDataController {

    private final PriceBarRepository priceBarRepository;
    private final DailyIndicatorRepository indicatorRepository;
    private final MarketDataScheduler scheduler;
    private final DataBackfillService dataBackfillService;

    @GetMapping("/bars/{ticker}")
    public ResponseEntity<List<PriceBar>> getBars(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(priceBarRepository.findRecentDailyBars(ticker.toUpperCase(), limit));
    }

    @GetMapping("/indicators/{ticker}/latest")
    public ResponseEntity<DailyIndicator> getLatestIndicator(@PathVariable String ticker) {
        Optional<DailyIndicator> indicator = indicatorRepository.findFirstByTickerOrderByIndicatorDateDesc(ticker.toUpperCase());
        return indicator.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/indicators/{ticker}/{date}")
    public ResponseEntity<DailyIndicator> getIndicator(
            @PathVariable String ticker,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return indicatorRepository.findByTickerAndIndicatorDate(ticker.toUpperCase(), date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/backfill/{ticker}")
    public ResponseEntity<Map<String, Object>> triggerBackfill(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Manual backfill trigger: {} from {} to {}", ticker, from, to);
        int count = scheduler.backfillTicker(ticker.toUpperCase(), from, to);
        return ResponseEntity.ok(Map.of("ticker", ticker, "barsProcessed", count, "status", "SUCCESS"));
    }

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestParam String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int count = dataBackfillService.backfillTicker(ticker, from, to);
        return ResponseEntity.ok(Map.of("ticker", ticker, "barsLoaded", count));
    }

    @PostMapping("/backfill/batch")
    public ResponseEntity<Map<String, Object>> backfillBatch(
            @RequestBody List<String> tickers,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int count = dataBackfillService.backfillUniverse(tickers, from, to);
        return ResponseEntity.ok(Map.of("tickerCount", tickers.size(), "totalBarsLoaded", count));
    }
}
