package org.pead.marketdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.client.YahooFinanceClient;
import org.pead.marketdata.client.dto.OhlcvDto;
import org.pead.marketdata.domain.PriceBar;
import org.pead.marketdata.repository.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Service for backfilling historical OHLCV data from Yahoo Finance
 * into the local price_bars table for Indian NSE/BSE tickers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataBackfillService {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final YahooFinanceClient yahooClient;
    private final PriceBarRepository priceBarRepository;
    private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * Backfill daily price bars for a single ticker from Yahoo Finance.
     *
     * @param ticker NSE ticker symbol (e.g. "RELIANCE")
     * @param from   start date (inclusive)
     * @param to     end date (inclusive)
     * @return number of new bars saved
     */
    @Transactional
    public int backfillTicker(String ticker, LocalDate from, LocalDate to) {
        log.info("Starting Yahoo Finance backfill for {} from {} to {}", ticker, from, to);

        List<OhlcvDto> bars = yahooClient.getDailyBars(ticker, from, to);
        if (bars.isEmpty()) {
            log.warn("No bars returned from Yahoo Finance for {}", ticker);
            return 0;
        }

        int saved = 0;
        for (OhlcvDto dto : bars) {
            // Yahoo timestamps are already stored as millis (converted in YahooFinanceClient)
            LocalDate barDate = Instant.ofEpochMilli(dto.getTimestamp())
                    .atZone(KOLKATA)
                    .toLocalDate();

            if (priceBarRepository.existsByTickerAndBarDate(ticker, barDate)) {
                log.debug("Skipping existing bar for {} on {}", ticker, barDate);
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
                    .volume(dto.getVolume() != null ? dto.getVolume() : 0L)
                    .vwap(dto.getVwap())
                    .build();

            priceBarRepository.save(priceBar);
            saved++;

            // Compute technical indicators for this bar (best-effort; log failures)
            try {
                technicalIndicatorService.computeAndSave(ticker, barDate);
            } catch (Exception e) {
                log.warn("Failed to compute indicators for {} on {}: {}", ticker, barDate, e.getMessage());
            }
        }

        log.info("Yahoo Finance backfill complete for {}: {}/{} bars saved", ticker, saved, bars.size());
        return saved;
    }

    /**
     * Backfill daily price bars for multiple tickers.
     *
     * @param tickers list of NSE ticker symbols
     * @param from    start date (inclusive)
     * @param to      end date (inclusive)
     * @return total number of new bars saved across all tickers
     */
    @Transactional
    public int backfillUniverse(List<String> tickers, LocalDate from, LocalDate to) {
        log.info("Starting Yahoo Finance universe backfill for {} tickers from {} to {}",
                tickers.size(), from, to);

        int total = 0;
        for (String ticker : tickers) {
            try {
                total += backfillTicker(ticker, from, to);
            } catch (Exception e) {
                log.error("Backfill failed for ticker {}: {}", ticker, e.getMessage());
            }
        }

        log.info("Universe backfill complete: {} total bars saved across {} tickers",
                total, tickers.size());
        return total;
    }
}
