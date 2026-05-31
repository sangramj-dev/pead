package org.pead.strategyvalidator.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.strategyvalidator.repository.ValidatedSignalRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Expires ACTIVE validated signals that have passed their Day+2 breakout window.
 *
 * <p>Runs at 17:05 ET on weekdays — after market close and EOD data ingestion.
 * Signals not triggered within 2 trading days are marked EXPIRED so downstream
 * services (signal-engine, paper-trading) stop monitoring them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalExpiryScheduler {

    private final ValidatedSignalRepository validatedSignalRepository;

    /**
     * Expire all ACTIVE signals whose expiryDate < today.
     * Runs Monday–Friday at 17:05 America/New_York.
     */
    @Scheduled(cron = "0 5 17 * * MON-FRI", zone = "America/New_York")
    public void expireStaleSignals() {
        LocalDate today = LocalDate.now();
        int expired = validatedSignalRepository.expireOldSignals(today);
        if (expired > 0) {
            log.info("Expired {} stale ACTIVE signal(s) with expiryDate < {}", expired, today);
        } else {
            log.debug("No stale signals to expire on {}", today);
        }
    }
}
