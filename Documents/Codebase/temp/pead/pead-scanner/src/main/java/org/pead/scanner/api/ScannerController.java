package org.pead.scanner.api;

import lombok.RequiredArgsConstructor;
import org.pead.scanner.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/scanner")
@RequiredArgsConstructor
public class ScannerController {

    private final WatchlistService watchlistService;

    @GetMapping("/watchlist")
    public ResponseEntity<Set<String>> getWatchlist() {
        return ResponseEntity.ok(watchlistService.getWatchlist());
    }

    @GetMapping("/watchlist/{ticker}")
    public ResponseEntity<Map<String, Boolean>> isOnWatchlist(@PathVariable String ticker) {
        return ResponseEntity.ok(Map.of("onWatchlist", watchlistService.isOnWatchlist(ticker)));
    }

    @DeleteMapping("/watchlist/{ticker}")
    public ResponseEntity<Void> removeFromWatchlist(@PathVariable String ticker) {
        watchlistService.removeFromWatchlist(ticker);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "pead-scanner"));
    }
}
