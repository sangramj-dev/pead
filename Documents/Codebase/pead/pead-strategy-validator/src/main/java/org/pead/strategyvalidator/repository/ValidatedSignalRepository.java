package org.pead.strategyvalidator.repository;

import org.pead.common.domain.SignalStatus;
import org.pead.strategyvalidator.domain.ValidatedSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ValidatedSignalRepository extends JpaRepository<ValidatedSignal, UUID> {

    List<ValidatedSignal> findByTickerAndStatus(String ticker, SignalStatus status);

    List<ValidatedSignal> findBySignalDate(LocalDate signalDate);

    List<ValidatedSignal> findByExpiryDateBeforeAndStatus(LocalDate date, SignalStatus status);

    @Modifying
    @Query("UPDATE ValidatedSignal s SET s.status = 'EXPIRED' WHERE s.expiryDate < :today AND s.status = 'ACTIVE'")
    int expireOldSignals(LocalDate today);
}
