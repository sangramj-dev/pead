package org.pead.backtesting.api;

import org.pead.backtesting.domain.BacktestEntity;
import org.pead.backtesting.domain.BacktestEquityCurveEntity;
import org.pead.backtesting.domain.BacktestTradeEntity;
import org.pead.backtesting.dto.BacktestRequest;
import org.pead.backtesting.service.BacktestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for backtest operations.
 */
@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * Submit a new backtest for async execution.
     * Returns the backtest ID immediately; poll GET /{id} for results.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> runBacktest(@RequestBody BacktestRequest request) {
        UUID backtestId = backtestService.submitBacktest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "backtestId", backtestId,
                        "status", "RUNNING",
                        "message", "Backtest submitted. Poll GET /api/v1/backtests/" + backtestId + " for results."
                ));
    }

    /**
     * Get backtest result summary.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BacktestEntity> getBacktest(@PathVariable UUID id) {
        return backtestService.getBacktest(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get trade list for a backtest.
     */
    @GetMapping("/{id}/trades")
    public ResponseEntity<List<BacktestTradeEntity>> getTrades(@PathVariable UUID id) {
        return backtestService.getBacktest(id)
                .map(bt -> ResponseEntity.ok(backtestService.getTrades(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get equity curve for a backtest.
     */
    @GetMapping("/{id}/equity")
    public ResponseEntity<List<BacktestEquityCurveEntity>> getEquityCurve(@PathVariable UUID id) {
        return backtestService.getBacktest(id)
                .map(bt -> ResponseEntity.ok(backtestService.getEquityCurve(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all backtests.
     */
    @GetMapping
    public ResponseEntity<List<BacktestEntity>> listBacktests() {
        return ResponseEntity.ok(backtestService.listBacktests());
    }
}
