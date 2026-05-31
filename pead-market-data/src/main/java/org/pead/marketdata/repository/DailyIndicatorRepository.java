package org.pead.marketdata.repository;

import org.pead.marketdata.domain.DailyIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyIndicatorRepository extends JpaRepository<DailyIndicator, Long> {

    Optional<DailyIndicator> findByTickerAndIndicatorDate(String ticker, LocalDate date);

    List<DailyIndicator> findByTickerOrderByIndicatorDateDesc(String ticker);

    Optional<DailyIndicator> findFirstByTickerOrderByIndicatorDateDesc(String ticker);
}
