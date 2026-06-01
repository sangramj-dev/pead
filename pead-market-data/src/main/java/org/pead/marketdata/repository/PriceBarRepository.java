package org.pead.marketdata.repository;

import org.pead.marketdata.domain.PriceBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    Optional<PriceBar> findByTickerAndBarDateAndTimeframe(String ticker, LocalDate barDate, String timeframe);

    @Query("SELECT p FROM PriceBar p WHERE p.ticker = :ticker AND p.timeframe = '1D' ORDER BY p.barDate DESC LIMIT :limit")
    List<PriceBar> findRecentDailyBars(@Param("ticker") String ticker, @Param("limit") int limit);

    @Query("SELECT p FROM PriceBar p WHERE p.ticker = :ticker AND p.barDate BETWEEN :startDate AND :endDate AND p.timeframe = '1D' ORDER BY p.barDate ASC")
    List<PriceBar> findDailyBarsInRange(@Param("ticker") String ticker,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    boolean existsByTickerAndBarDateAndTimeframe(String ticker, LocalDate barDate, String timeframe);

    boolean existsByTickerAndBarDate(String ticker, LocalDate barDate);
}
