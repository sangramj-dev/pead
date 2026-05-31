package org.pead.scanner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.CandidateSymbolEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private static final String WATCHLIST_KEY = "scanner:watchlist";
    private static final String CANDIDATE_PREFIX = "scanner:candidate:";
    private static final long CANDIDATE_TTL_SECONDS = 86_400L;

    private final StringRedisTemplate redisTemplate;

    public void addToWatchlist(CandidateSymbolEvent candidate) {
        String ticker = candidate.getTicker();

        redisTemplate.opsForSet().add(WATCHLIST_KEY, ticker);

        String candidateKey = CANDIDATE_PREFIX + ticker;
        Map<String, String> fields = buildCandidateFields(candidate, ticker);
        redisTemplate.opsForHash().putAll(candidateKey, fields);
        redisTemplate.expire(candidateKey, Duration.ofSeconds(CANDIDATE_TTL_SECONDS));

        log.info("Added {} to watchlist (scanDate={}, gap={}%, relVol={})",
                ticker,
                candidate.getScanDate(),
                candidate.getGapPct(),
                candidate.getRelativeVolume());
    }

    public Set<String> getWatchlist() {
        Set<String> members = redisTemplate.opsForSet().members(WATCHLIST_KEY);
        return members != null ? members : Set.of();
    }

    public boolean isOnWatchlist(String ticker) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(WATCHLIST_KEY, ticker));
    }

    public void removeFromWatchlist(String ticker) {
        redisTemplate.opsForSet().remove(WATCHLIST_KEY, ticker);
        redisTemplate.delete(CANDIDATE_PREFIX + ticker);
        log.info("Removed {} from watchlist", ticker);
    }

    private Map<String, String> buildCandidateFields(CandidateSymbolEvent candidate, String ticker) {
        Map<String, String> fields = new HashMap<>();
        fields.put("ticker", ticker);
        fields.put("eventId", candidate.getEventId());
        fields.put("correlationId", candidate.getCorrelationId());
        fields.put("scanDate", String.valueOf(candidate.getScanDate()));
        fields.put("direction", candidate.getDirection().toString());
        fields.put("earningsEventId", candidate.getEarningsEventId());
        fields.put("gapEventId", candidate.getGapEventId());
        fields.put("earningsDate", String.valueOf(candidate.getEarningsDate()));
        fields.put("epsSurprisePct", String.valueOf(candidate.getEpsSurprisePct()));
        fields.put("revenueSurprisePct", String.valueOf(candidate.getRevenueSurprisePct()));
        fields.put("gapPct", String.valueOf(candidate.getGapPct()));
        fields.put("relativeVolume", String.valueOf(candidate.getRelativeVolume()));
        fields.put("earningsCandleHigh", String.valueOf(candidate.getEarningsCandleHigh()));
        fields.put("earningsCandleLow", String.valueOf(candidate.getEarningsCandleLow()));
        fields.put("earningsCandleClose", String.valueOf(candidate.getEarningsCandleClose()));
        fields.put("preFilterScore", String.valueOf(candidate.getPreFilterScore()));
        fields.put("eventTimestamp", String.valueOf(candidate.getEventTimestamp()));
        return fields;
    }
}
