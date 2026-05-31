package org.pead.earnings.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.domain.AnnouncementTime;
import org.pead.earnings.client.dto.EarningsDto;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Client for Financial Modeling Prep (FMP) Earnings Calendar API.
 */
@Component
@Slf4j
public class FmpEarningsClient {

    private final WebClient fmpWebClient;

    public FmpEarningsClient(@Qualifier("fmpWebClient") WebClient fmpWebClient) {
        this.fmpWebClient = fmpWebClient;
    }

    @Value("${pead.data-sources.fmp.api-key:}")
    private String apiKey;

    @CircuitBreaker(name = "fmp-earnings", fallbackMethod = "fallbackEarnings")
    public List<EarningsDto> getEarningsCalendar(LocalDate date) {
        if (apiKey.isBlank()) {
            log.debug("FMP API key not configured, skipping");
            return Collections.emptyList();
        }

        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.debug("Fetching earnings from FMP for date: {}", dateStr);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = fmpWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/earning_calendar")
                            .queryParam("from", dateStr)
                            .queryParam("to", dateStr)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (results == null) return Collections.emptyList();

            return results.stream()
                    .map(this::mapToEarningsDto)
                    .filter(dto -> dto != null)
                    .toList();

        } catch (Exception e) {
            log.error("Error fetching FMP earnings for {}: {}", dateStr, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private EarningsDto mapToEarningsDto(Map<String, Object> result) {
        try {
            String ticker = (String) result.get("symbol");
            if (ticker == null) return null;
            String dateStr = (String) result.get("date");
            if (dateStr == null) return null;

            LocalDate announcementDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            String timeStr = (String) result.getOrDefault("time", "");
            AnnouncementTime time = timeStr.equalsIgnoreCase("bmo") ? AnnouncementTime.PRE_MARKET :
                                    timeStr.equalsIgnoreCase("amc") ? AnnouncementTime.POST_MARKET :
                                    AnnouncementTime.UNKNOWN;

            BigDecimal epsActual = extractBigDecimal(result, "eps");
            BigDecimal epsEstimate = extractBigDecimal(result, "epsEstimated");
            Long revenueActual = extractLong(result, "revenue");
            Long revenueEstimate = extractLong(result, "revenueEstimated");

            return EarningsDto.builder()
                    .ticker(ticker)
                    .announcementDate(announcementDate)
                    .announcementTime(time)
                    .fiscalQuarter((String) result.getOrDefault("fiscalDateEnding", ""))
                    .epsActual(epsActual)
                    .epsEstimate(epsEstimate)
                    .revenueActual(revenueActual)
                    .revenueEstimate(revenueEstimate)
                    .source("FMP")
                    .rawPayload(result.toString())
                    .build();
        } catch (Exception e) {
            log.error("Error mapping FMP result: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        if (val instanceof String) {
            try { return new BigDecimal((String) val); } catch (Exception ignored) {}
        }
        return null;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    public List<EarningsDto> fallbackEarnings(LocalDate date, Exception ex) {
        log.warn("FMP circuit breaker open for date {}: {}", date, ex.getMessage());
        return Collections.emptyList();
    }
}
