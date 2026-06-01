package org.pead.signalengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.ValidatedSignalEvent;
import org.pead.signalengine.config.SignalEngineConfig;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed store for pending validated signals awaiting breakout confirmation.
 * Key format: pending-signal:{ticker}:{signalId}
 * Each signal has a TTL equal to the configured expiry days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingSignalStore {

    private static final String KEY_PREFIX = "pending-signal:";

    private final RedisTemplate<String, ValidatedSignalEvent> validatedSignalRedisTemplate;
    private final SignalEngineConfig signalEngineConfig;

    /**
     * Store a validated signal in Redis with a TTL based on configuration.
     */
    public void store(ValidatedSignalEvent signal) {
        String key = buildKey(signal.getTicker(), signal.getSignalId());
        int expiryDays = signalEngineConfig.signalExpiryDays();

        validatedSignalRedisTemplate.opsForValue().set(key, signal, expiryDays, TimeUnit.DAYS);
        log.info("Stored pending signal: ticker={}, signalId={}, direction={}, entryPrice={}, TTL={}d",
                signal.getTicker(), signal.getSignalId(), signal.getDirection(),
                signal.getEntryPrice(), expiryDays);
    }

    /**
     * Retrieve all pending signals for a given ticker.
     */
    public List<ValidatedSignalEvent> getByTicker(String ticker) {
        String pattern = KEY_PREFIX + ticker + ":*";
        Set<String> keys = validatedSignalRedisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<ValidatedSignalEvent> signals = new ArrayList<>();
        for (String key : keys) {
            ValidatedSignalEvent signal = validatedSignalRedisTemplate.opsForValue().get(key);
            if (signal != null) {
                signals.add(signal);
            }
        }
        return signals;
    }

    /**
     * Remove a triggered signal from the store.
     */
    public void remove(String ticker, String signalId) {
        String key = buildKey(ticker, signalId);
        Boolean deleted = validatedSignalRedisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Removed triggered signal: ticker={}, signalId={}", ticker, signalId);
        }
    }

    /**
     * Cleanup expired signals. Redis TTL handles most of this automatically,
     * but this can be called for explicit cleanup if needed.
     */
    public void removeExpired() {
        Set<String> keys = validatedSignalRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int removed = 0;
        for (String key : keys) {
            Long ttl = validatedSignalRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl != null && ttl <= 0) {
                validatedSignalRedisTemplate.delete(key);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Removed {} expired pending signals", removed);
        }
    }

    private String buildKey(String ticker, String signalId) {
        return KEY_PREFIX + ticker + ":" + signalId;
    }
}
