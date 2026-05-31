package org.pead.marketdata.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.client.dto.OhlcvDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST client for Polygon.io Aggregates (OHLCV bars) API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolygonRestClient {

    private final WebClient polygonWebClient;

    @Value("${pead.data-sources.polygon.api-key:}")
    private String apiKey;

    @CircuitBreaker(name = "polygon-market-data", fallbackMethod = "fallback")
    @Retry(name = "polygon-market-data")
    public List<OhlcvDto> getDailyBars(String ticker, LocalDate from, LocalDate to) {
        if (apiKey.isBlank()) {
            log.warn("Polygon API key not configured");
            return Collections.emptyList();
        }

        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toStr = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        log.debug("Fetching daily bars for {} from {} to {}", ticker, fromStr, toStr);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = polygonWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}")
                            .queryParam("adjusted", true)
                            .queryParam("sort", "asc")
                            .queryParam("limit", 300)
                            .queryParam("apiKey", apiKey)
                            .build(ticker, fromStr, toStr))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("results")) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            return results.stream().map(r -> mapToDto(ticker, r)).toList();

        } catch (Exception e) {
            log.error("Error fetching Polygon bars for {}: {}", ticker, e.getMessage());
            throw e;
        }
    }

    private OhlcvDto mapToDto(String ticker, Map<String, Object> r) {
        return OhlcvDto.builder()
                .ticker(ticker)
                .open(extractBd(r, "o"))
                .high(extractBd(r, "h"))
                .low(extractBd(r, "l"))
                .close(extractBd(r, "c"))
                .volume(extractLong(r, "v"))
                .vwap(extractBd(r, "vw"))
                .timestamp(extractLong(r, "t"))
                .build();
    }

    private BigDecimal extractBd(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return null;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    public List<OhlcvDto> fallback(String ticker, LocalDate from, LocalDate to, Exception ex) {
        log.warn("Polygon market data circuit breaker for {}: {}", ticker, ex.getMessage());
        return Collections.emptyList();
    }
}
