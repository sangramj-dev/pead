package org.pead.strategyvalidator.client;

import lombok.extern.slf4j.Slf4j;
import org.pead.strategyvalidator.client.dto.DailyIndicatorDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * REST client for fetching indicator data from pead-market-data service.
 * Used to enrich candidates with EMA20/EMA50 data before scoring.
 */
@Component
@Slf4j
public class MarketDataClient {

    private final WebClient webClient;

    public MarketDataClient(
            WebClient.Builder webClientBuilder,
            @Value("${pead.market-data.base-url:http://localhost:8082}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Fetches the latest daily indicator for the given ticker.
     *
     * @param ticker the stock ticker symbol
     * @return Optional containing DailyIndicatorDto, or empty if unavailable
     */
    public Optional<DailyIndicatorDto> getLatestIndicator(String ticker) {
        try {
            DailyIndicatorDto dto = webClient.get()
                    .uri("/api/v1/market/indicators/{ticker}/latest", ticker)
                    .retrieve()
                    .bodyToMono(DailyIndicatorDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return Optional.ofNullable(dto);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("No indicator data found for ticker: {}", ticker);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch indicator for {}: {}. Scoring without EMA data.", ticker, e.getMessage());
            return Optional.empty();
        }
    }
}
