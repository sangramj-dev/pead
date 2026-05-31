package org.pead.strategyvalidator.api;

import lombok.RequiredArgsConstructor;
import org.pead.common.domain.SignalStatus;
import org.pead.strategyvalidator.domain.RejectedCandidate;
import org.pead.strategyvalidator.domain.ScoringDetail;
import org.pead.strategyvalidator.domain.ValidatedSignal;
import org.pead.strategyvalidator.repository.RejectedCandidateRepository;
import org.pead.strategyvalidator.repository.ScoringDetailRepository;
import org.pead.strategyvalidator.repository.ValidatedSignalRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class StrategyValidatorController {

    private final ValidatedSignalRepository signalRepository;
    private final ScoringDetailRepository scoringDetailRepository;
    private final RejectedCandidateRepository rejectedCandidateRepository;

    @GetMapping("/validated")
    public ResponseEntity<List<ValidatedSignal>> getValidatedByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(signalRepository.findBySignalDate(date));
    }

    @GetMapping("/validated/{ticker}")
    public ResponseEntity<List<ValidatedSignal>> getValidatedByTicker(
            @PathVariable String ticker) {
        return ResponseEntity.ok(signalRepository.findByTickerAndStatus(ticker, SignalStatus.ACTIVE));
    }

    @GetMapping("/validated/{signalId}/scoring")
    public ResponseEntity<List<ScoringDetail>> getScoringDetails(
            @PathVariable UUID signalId) {
        return ResponseEntity.ok(scoringDetailRepository.findBySignalId(signalId));
    }

    @GetMapping("/rejected")
    public ResponseEntity<List<RejectedCandidate>> getRejectedByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(rejectedCandidateRepository.findByScanDate(date));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "pead-strategy-validator"));
    }
}
