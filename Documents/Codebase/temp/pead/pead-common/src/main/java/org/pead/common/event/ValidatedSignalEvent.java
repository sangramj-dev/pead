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
public class ValidatedSignalEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String signalId;
    private String ticker;
    private TradeDirection direction;
    private LocalDate signalDate;
    private LocalDate earningsDate;
    private int peadScore;
    private double entryPrice;
    private double stopLoss;
    private double target1;
    private double target2;
    private double riskRewardRatio;
    private double epsSurprisePct;
    private double revenueSurprisePct;
    private double gapPct;
    private double relativeVolume;
    private double ema20;
    private double ema50;
    private boolean aboveEma20;
    private boolean aboveEma50;
    private boolean closeNearHigh;
    private LocalDate expiryDate;
}
