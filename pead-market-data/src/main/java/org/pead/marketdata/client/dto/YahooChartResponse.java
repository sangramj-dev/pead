package org.pead.marketdata.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result, Object error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Long> timestamp, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote, List<AdjClose> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(List<Double> open, List<Double> high, List<Double> low,
                        List<Double> close, List<Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdjClose(List<Double> adjclose) {}
}
