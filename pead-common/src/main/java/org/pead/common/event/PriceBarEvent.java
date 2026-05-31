package org.pead.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBarEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String ticker;
    private LocalDate barDate;
    private String timeframe;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private Double vwap;
    private Double ema20;
    private Double ema50;
    private Double relativeVolume;
}
