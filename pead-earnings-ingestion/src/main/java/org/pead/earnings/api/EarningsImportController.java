package org.pead.earnings.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.earnings.client.ScreenerClient;
import org.pead.earnings.client.dto.EarningsDto;
import org.pead.earnings.service.CsvImportService;
import org.pead.earnings.service.EarningsIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for importing earnings data via CSV upload or Screener.in scraping.
 */
@RestController
@RequestMapping("/api/v1/earnings")
@RequiredArgsConstructor
@Slf4j
public class EarningsImportController {

    private final CsvImportService csvImportService;
    private final ScreenerClient screenerClient;
    private final EarningsIngestionService ingestionService;

    /**
     * Imports earnings from an uploaded CSV file.
     *
     * <p>Expected CSV columns (header on first line):
     * {@code ticker,date,quarter,eps_actual,eps_estimate,revenue_actual,revenue_estimate,source}
     *
     * @param file the CSV file to import
     * @return JSON with the count of successfully imported records
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file) {

        log.info("CSV import request: filename={} size={}",
                file.getOriginalFilename(), file.getSize());

        int count = csvImportService.importCsv(file);

        return ResponseEntity.ok(Map.of("imported", count));
    }

    /**
     * Scrapes quarterly results for the given ticker from Screener.in and persists them.
     *
     * @param ticker the NSE/BSE ticker symbol (e.g. "TATAELXSI")
     * @return JSON with ticker and count of records found/saved
     */
    @PostMapping("/scrape/{ticker}")
    public ResponseEntity<Map<String, Object>> scrapeScreener(
            @PathVariable String ticker) {

        log.info("Screener.in scrape request: ticker={}", ticker);

        List<EarningsDto> results = screenerClient.getQuarterlyResults(ticker);

        int saved = 0;
        for (EarningsDto dto : results) {
            try {
                ingestionService.ingestEarnings(dto);
                saved++;
            } catch (Exception e) {
                log.error("Failed to ingest scraped record for ticker={} quarter={}: {}",
                        ticker, dto.getFiscalQuarter(), e.getMessage());
            }
        }

        log.info("Screener.in scrape complete: ticker={} found={} saved={}",
                ticker, results.size(), saved);

        return ResponseEntity.ok(Map.of(
                "ticker", ticker.toUpperCase(),
                "recordsFound", results.size(),
                "recordsSaved", saved
        ));
    }
}
