package org.pead.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntrySignalEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String ticker;
    private String signalId;
    private String direction;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal target1;
    private BigDecimal target2;
    private int quantity;
    private int peadScore;
}
