package org.pead.earnings.repository;

import org.pead.earnings.domain.EarningsAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EarningsAnnouncementRepository extends JpaRepository<EarningsAnnouncement, Long> {

    Optional<EarningsAnnouncement> findByTickerAndAnnouncementDateAndFiscalQuarter(
            String ticker, LocalDate announcementDate, String fiscalQuarter);

    List<EarningsAnnouncement> findByAnnouncementDate(LocalDate date);

    @Query("SELECT e FROM EarningsAnnouncement e WHERE e.announcementDate BETWEEN :startDate AND :endDate ORDER BY e.announcementDate DESC")
    List<EarningsAnnouncement> findByDateRange(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM EarningsAnnouncement e WHERE e.bothBeat = true AND e.announcementDate BETWEEN :startDate AND :endDate")
    List<EarningsAnnouncement> findBothBeatInRange(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    boolean existsByTickerAndAnnouncementDateAndFiscalQuarter(
            String ticker, LocalDate announcementDate, String fiscalQuarter);
}
