package org.pead.scanner.service;

import lombok.extern.slf4j.Slf4j;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.CandidateSymbolEvent;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ScanCriteriaEvaluator {

    public boolean meetsPreFilterCriteria(CandidateSymbolEvent candidate, StrategyConfig config) {

        String ticker = candidate.getTicker() != null ? candidate.getTicker() : "UNKNOWN";

        if (TradeDirection.LONG == candidate.getDirection()
                && candidate.getEpsSurprisePct() <= 0) {
            log.debug("[{}] Filtered: epsSurprisePct={} <= 0 for LONG candidate", ticker, candidate.getEpsSurprisePct());
            return false;
        }

        double absGap = Math.abs(candidate.getGapPct());
        if (absGap < config.minGapPct()) {
            log.debug("[{}] Filtered: absGapPct={} < minGapPct={}", ticker, absGap, config.minGapPct());
            return false;
        }

        if (TradeDirection.LONG == candidate.getDirection() && candidate.getGapPct() < 0) {
            log.debug("[{}] Filtered: LONG candidate has negative gapPct={}", ticker, candidate.getGapPct());
            return false;
        }
        if (TradeDirection.SHORT == candidate.getDirection() && candidate.getGapPct() > 0) {
            log.debug("[{}] Filtered: SHORT candidate has positive gapPct={}", ticker, candidate.getGapPct());
            return false;
        }

        if (candidate.getRelativeVolume() < config.minRelativeVolume()) {
            log.debug("[{}] Filtered: relativeVolume={} < minRelativeVolume={}",
                    ticker, candidate.getRelativeVolume(), config.minRelativeVolume());
            return false;
        }

        if (!config.enableShortSelling() && TradeDirection.SHORT == candidate.getDirection()) {
            log.debug("[{}] Filtered: SHORT selling disabled", ticker);
            return false;
        }

        log.debug("[{}] Passed pre-filter: gap={}%, relVol={}, epsSurprise={}%",
                ticker, candidate.getGapPct(), candidate.getRelativeVolume(),
                candidate.getEpsSurprisePct());
        return true;
    }
}
