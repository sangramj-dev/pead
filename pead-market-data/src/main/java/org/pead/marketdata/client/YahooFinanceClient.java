package org.pead.marketdata.client;

import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.client.dto.OhlcvDto;
import org.pead.marketdata.client.dto.YahooChartResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * REST client for Yahoo Finance chart API (v8).
 * Supports Indian NSE stocks by appending the ".NS" suffix.
 * No API key required; uses a browser-like User-Agent header.
 */
@Component
@Slf4j
public class YahooFinanceClient {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final String NS_SUFFIX = ".NS";

    private final WebClient webClient;
    private final long requestDelayMs;

    public YahooFinanceClient(
            @Value("${pead.data-sources.yahoo-finance.base-url:https://query1.finance.yahoo.com}") String baseUrl,
            @Value("${pead.data-sources.yahoo-finance.request-delay-ms:200}") long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Fetch daily OHLCV bars for a single NSE ticker.
     *
     * @param ticker raw ticker symbol (e.g. "RELIANCE" or "RELIANCE.NS")
     * @param from   start date (inclusive), Asia/Kolkata
     * @param to     end date (inclusive), Asia/Kolkata
     * @return list of OhlcvDto bars sorted by timestamp ascending; empty on error
     */
    public List<OhlcvDto> getDailyBars(String ticker, LocalDate from, LocalDate to) {
        String symbol = toNseSymbol(ticker);
        long period1 = from.atStartOfDay(KOLKATA).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(KOLKATA).toEpochSecond(); // end of the 'to' day

        log.debug("Fetching Yahoo Finance bars for {} ({}) from {} to {}", ticker, symbol, from, to);

        try {
            YahooChartResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "1d")
                            .queryParam("events", "history")
                            .build(symbol))
                    .retrieve()
                    .bodyToMono(YahooChartResponse.class)
                    .block();

            return parseBars(ticker, response);

        } catch (Exception e) {
            log.error("Error fetching Yahoo Finance bars for {}: {}", ticker, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch daily bars for multiple tickers sequentially with a configurable delay between calls.
     *
     * @param tickers list of ticker symbols
     * @param from    start date
     * @param to      end date
     * @return flat list of all OhlcvDto bars across all tickers
     */
    public List<OhlcvDto> batchFetch(List<String> tickers, LocalDate from, LocalDate to) {
        return tickers.stream()
                .flatMap(ticker -> {
                    List<OhlcvDto> bars = getDailyBars(ticker, from, to);
                    if (requestDelayMs > 0) {
                        try {
                            Thread.sleep(requestDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Yahoo Finance batch fetch interrupted");
                        }
                    }
                    return bars.stream();
                })
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String toNseSymbol(String ticker) {
        if (ticker == null) return "";
        String upper = ticker.trim().toUpperCase();
        return upper.endsWith(NS_SUFFIX) ? upper : upper + NS_SUFFIX;
    }

    private List<OhlcvDto> parseBars(String ticker, YahooChartResponse response) {
        if (response == null || response.chart() == null) {
            return Collections.emptyList();
        }

        YahooChartResponse.Chart chart = response.chart();

        if (chart.error() != null) {
            log.warn("Yahoo Finance returned error for {}: {}", ticker, chart.error());
            return Collections.emptyList();
        }

        List<YahooChartResponse.Result> results = chart.result();
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        YahooChartResponse.Result result = results.get(0);
        List<Long> timestamps = result.timestamp();

        if (timestamps == null || timestamps.isEmpty()
                || result.indicators() == null
                || result.indicators().quote() == null
                || result.indicators().quote().isEmpty()) {
            return Collections.emptyList();
        }

        YahooChartResponse.Quote quote = result.indicators().quote().get(0);
        List<Double> opens   = nullSafe(quote.open());
        List<Double> highs   = nullSafe(quote.high());
        List<Double> lows    = nullSafe(quote.low());
        List<Double> closes  = nullSafe(quote.close());
        List<Long>   volumes = nullSafeLong(quote.volume());

        int size = timestamps.size();
        List<OhlcvDto> bars = new java.util.ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Long ts    = safeGet(timestamps, i);
            Double o   = safeGet(opens,   i);
            Double h   = safeGet(highs,   i);
            Double l   = safeGet(lows,    i);
            Double c   = safeGet(closes,  i);
            Long   vol = safeGet(volumes, i);

            // Skip bars where any OHLC value is null
            if (ts == null || o == null || h == null || l == null || c == null) {
                log.debug("Skipping null OHLC bar at index {} for {}", i, ticker);
                continue;
            }

            bars.add(OhlcvDto.builder()
                    .ticker(ticker)
                    .open(BigDecimal.valueOf(o))
                    .high(BigDecimal.valueOf(h))
                    .low(BigDecimal.valueOf(l))
                    .close(BigDecimal.valueOf(c))
                    .volume(vol)
                    .vwap(null) // Yahoo chart API does not provide VWAP
                    .timestamp(ts * 1000L) // convert seconds to millis
                    .build());
        }

        log.debug("Parsed {} valid bars for {}", bars.size(), ticker);
        return bars;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private List<Long> nullSafeLong(List<Long> list) {
        return list != null ? list : Collections.emptyList();
    }

    private <T> T safeGet(List<T> list, int index) {
        if (list == null || index >= list.size()) return null;
        return list.get(index);
    }
}
