package org.pead.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pead.common.domain.GapDirection;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapDetectedEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String ticker;
    private LocalDate tradeDate;
    private double prevClose;
    private double openPrice;
    private double gapPct;
    private GapDirection gapDirection;
    private double relativeVolume;
    private boolean earningsRelated;
    private LocalDate earningsDate;
}
