package org.pead.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pead.common.domain.AnnouncementTime;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsAnnouncementEvent {
    private String eventId;
    private String correlationId;
    private long eventTimestamp;
    private String ticker;
    private String companyName;
    private LocalDate announcementDate;
    private AnnouncementTime announcementTime;
    private String fiscalQuarter;
    private int fiscalYear;
    private Double epsActual;
    private Double epsEstimate;
    private Double epsSurprisePct;
    private boolean epsBeat;
    private Long revenueActual;
    private Long revenueEstimate;
    private Double revenueSurprisePct;
    private boolean revenueBeat;
    private boolean bothBeat;
    private String source;
}
