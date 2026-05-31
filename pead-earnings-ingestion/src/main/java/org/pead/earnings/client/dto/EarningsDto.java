package org.pead.earnings.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pead.common.domain.AnnouncementTime;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Normalized earnings data transfer object.
 * Adapters for Polygon, FMP, and Alpha Vantage all produce this DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsDto {
    private String ticker;
    private LocalDate announcementDate;
    private AnnouncementTime announcementTime;
    private String fiscalQuarter;
    private Integer fiscalYear;
    private BigDecimal epsActual;
    private BigDecimal epsEstimate;
    private Long revenueActual;
    private Long revenueEstimate;
    private String source;
    private String rawPayload;
}
