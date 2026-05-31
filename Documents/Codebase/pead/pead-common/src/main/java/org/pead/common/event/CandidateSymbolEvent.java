package org.pead.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pead.common.domain.TradeDirection;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSymbolEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String ticker;
    private LocalDate scanDate;
    private TradeDirection direction;
    private String earningsEventId;
    private String gapEventId;
    private LocalDate earningsDate;
    private double epsSurprisePct;
    private double revenueSurprisePct;
    private double gapPct;
    private double relativeVolume;
    private double earningsCandleHigh;
    private double earningsCandleLow;
    private double earningsCandleClose;
    private int preFilterScore;
}
