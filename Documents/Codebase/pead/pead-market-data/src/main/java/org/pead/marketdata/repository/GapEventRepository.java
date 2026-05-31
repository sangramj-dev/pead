package org.pead.marketdata.repository;

import org.pead.marketdata.domain.GapEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GapEventRepository extends JpaRepository<GapEvent, Long> {

    Optional<GapEvent> findByTickerAndGapDate(String ticker, LocalDate gapDate);

    @Query("SELECT g FROM GapEvent g WHERE g.earningsRelated = true AND g.gapDate BETWEEN :startDate AND :endDate ORDER BY g.gapDate DESC")
    List<GapEvent> findEarningsRelatedGaps(@Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    boolean existsByTickerAndGapDate(String ticker, LocalDate gapDate);
}
