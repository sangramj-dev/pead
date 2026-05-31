package org.pead.earnings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.domain.AnnouncementTime;
import org.pead.earnings.client.dto.EarningsDto;
import org.pead.earnings.domain.EarningsAnnouncement;
import org.pead.earnings.kafka.EarningsEventPublisher;
import org.pead.earnings.repository.EarningsAnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsIngestionService {

    private final EarningsAnnouncementRepository repository;
    private final EarningsSurpriseCalculator surpriseCalculator;
    private final EarningsEventPublisher eventPublisher;

    @Transactional
    public EarningsAnnouncement ingestEarnings(EarningsDto dto) {
        // Idempotency check
        if (repository.existsByTickerAndAnnouncementDateAndFiscalQuarter(
                dto.getTicker(), dto.getAnnouncementDate(), dto.getFiscalQuarter())) {
            log.debug("Earnings already ingested for {} on {} Q{}",
                    dto.getTicker(), dto.getAnnouncementDate(), dto.getFiscalQuarter());
            return repository.findByTickerAndAnnouncementDateAndFiscalQuarter(
                    dto.getTicker(), dto.getAnnouncementDate(), dto.getFiscalQuarter()).orElseThrow();
        }

        var epsSurprisePct = surpriseCalculator.calculateEpsSurprisePct(dto.getEpsActual(), dto.getEpsEstimate());
        var revSurprisePct = surpriseCalculator.calculateRevenueSurprisePct(dto.getRevenueActual(), dto.getRevenueEstimate());
        var epsBeat = surpriseCalculator.isEpsBeat(dto.getEpsActual(), dto.getEpsEstimate());
        var revBeat = surpriseCalculator.isRevenueBeat(dto.getRevenueActual(), dto.getRevenueEstimate());

        var announcement = EarningsAnnouncement.builder()
                .ticker(dto.getTicker())
                .announcementDate(dto.getAnnouncementDate())
                .announcementTime(dto.getAnnouncementTime() != null ? dto.getAnnouncementTime() : AnnouncementTime.UNKNOWN)
                .fiscalQuarter(dto.getFiscalQuarter())
                .fiscalYear(dto.getFiscalYear())
                .epsActual(dto.getEpsActual())
                .epsEstimate(dto.getEpsEstimate())
                .epsSurprisePct(epsSurprisePct)
                .epsBeat(epsBeat)
                .revenueActual(dto.getRevenueActual())
                .revenueEstimate(dto.getRevenueEstimate())
                .revenueSurprisePct(revSurprisePct)
                .revenueBeat(revBeat)
                .source(dto.getSource())
                .rawPayload(dto.getRawPayload())
                .build();

        // Save domain entity + write to outbox in ONE transaction
        var saved = repository.save(announcement);
        String correlationId = UUID.randomUUID().toString();
        eventPublisher.publishToOutbox(saved, correlationId);

        log.info("Ingested earnings for {} on {}: EPS beat={}, Rev beat={}, Score={}",
                saved.getTicker(), saved.getAnnouncementDate(), epsBeat, revBeat,
                epsBeat && revBeat ? "BOTH_BEAT" : "MISS");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EarningsAnnouncement> getEarningsByDate(LocalDate date) {
        return repository.findByAnnouncementDate(date);
    }

    @Transactional(readOnly = true)
    public List<EarningsAnnouncement> getEarningsByDateRange(LocalDate startDate, LocalDate endDate) {
        return repository.findByDateRange(startDate, endDate);
    }
}
