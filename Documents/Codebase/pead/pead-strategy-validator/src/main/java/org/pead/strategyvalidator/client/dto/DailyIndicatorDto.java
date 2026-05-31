package org.pead.strategyvalidator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * DTO for the DailyIndicator response from pead-market-data service.
 * GET /api/v1/market/indicators/{ticker}/latest
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyIndicatorDto(
        String ticker,
        String indicatorDate,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal sma200,
        Double relVolume,
        Boolean aboveEma20,
        Boolean aboveEma50
) {
    public boolean hasEmaData() {
        return ema20 != null && ema50 != null;
    }
}
