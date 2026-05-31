package org.pead.earnings.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.domain.AnnouncementTime;
import org.pead.earnings.client.dto.EarningsDto;
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
 * Client for Polygon.io Earnings API.
 * API key configured via environment variable POLYGON_API_KEY.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolygonEarningsClient {

    private final WebClient polygonWebClient;

    @Value("${pead.data-sources.polygon.api-key:}")
    private String apiKey;

    @CircuitBreaker(name = "polygon-earnings", fallbackMethod = "fallbackEarnings")
    @Retry(name = "polygon-earnings")
    public List<EarningsDto> getEarningsCalendar(LocalDate date) {
        if (apiKey.isBlank()) {
            log.warn("Polygon API key not configured, skipping earnings fetch");
            return Collections.emptyList();
        }

        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("Fetching earnings from Polygon for date: {}", dateStr);

        try {
            var response = polygonWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/vX/reference/financials")
                            .queryParam("filing_date", dateStr)
                            .queryParam("timeframe", "quarterly")
                            .queryParam("include_sources", true)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("results")) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            return results.stream()
                    .map(this::mapToEarningsDto)
                    .filter(dto -> dto != null)
                    .toList();

        } catch (Exception e) {
            log.error("Error fetching Polygon earnings for {}: {}", dateStr, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private EarningsDto mapToEarningsDto(Map<String, Object> result) {
        try {
            String ticker = (String) result.get("ticker");
            if (ticker == null) return null;

            var financials = (Map<String, Object>) result.get("financials");
            if (financials == null) return null;

            var incomeStatement = (Map<String, Object>) financials.get("income_statement");
            if (incomeStatement == null) return null;

            Double epsActual = extractDoubleValue(incomeStatement, "basic_earnings_per_share");
            Double revenueActual = extractDoubleValue(incomeStatement, "revenues");

            String startDate = (String) result.get("start_date");
            LocalDate announcementDate = startDate != null
                    ? LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
                    : LocalDate.now();

            return EarningsDto.builder()
                    .ticker(ticker)
                    .announcementDate(announcementDate)
                    .announcementTime(AnnouncementTime.UNKNOWN)
                    .fiscalQuarter((String) result.getOrDefault("fiscal_period", "Q1"))
                    .fiscalYear(extractIntValue(result, "fiscal_year"))
                    .epsActual(epsActual != null ? BigDecimal.valueOf(epsActual) : null)
                    .revenueActual(revenueActual != null ? revenueActual.longValue() : null)
                    .source("POLYGON")
                    .rawPayload(result.toString())
                    .build();
        } catch (Exception e) {
            log.error("Error mapping Polygon result: {}", e.getMessage());
            return null;
        }
    }

    private Double extractDoubleValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) {
            @SuppressWarnings("unchecked")
            Object value = ((Map<String, Object>) val).get("value");
            if (value instanceof Number) return ((Number) value).doubleValue();
        }
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    private Integer extractIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public List<EarningsDto> fallbackEarnings(LocalDate date, Exception ex) {
        log.warn("Polygon earnings circuit breaker open for date {}: {}", date, ex.getMessage());
        return Collections.emptyList();
    }
}
