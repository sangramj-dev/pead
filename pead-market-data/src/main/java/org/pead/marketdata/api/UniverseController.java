package org.pead.marketdata.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.marketdata.domain.StockUniverse;
import org.pead.marketdata.domain.UniverseStock;
import org.pead.marketdata.repository.StockUniverseRepository;
import org.pead.marketdata.repository.UniverseStockRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market/universes")
@RequiredArgsConstructor
@Slf4j
public class UniverseController {

    private final StockUniverseRepository universeRepository;
    private final UniverseStockRepository universeStockRepository;

    @GetMapping
    public ResponseEntity<List<StockUniverse>> listUniverses() {
        return ResponseEntity.ok(universeRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUniverse(@PathVariable UUID id) {
        return universeRepository.findById(id)
                .map(universe -> {
                    long stockCount = universeStockRepository.findByUniverseId(id).size();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "id", universe.getId(),
                            "name", universe.getName(),
                            "description", universe.getDescription(),
                            "isPredefined", universe.getIsPredefined(),
                            "createdAt", universe.getCreatedAt(),
                            "stockCount", stockCount
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stocks")
    public ResponseEntity<List<UniverseStock>> getUniverseStocks(@PathVariable UUID id) {
        if (!universeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(universeStockRepository.findByUniverseId(id));
    }

    @PostMapping
    public ResponseEntity<StockUniverse> createUniverse(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        StockUniverse universe = StockUniverse.builder()
                .name(name.toUpperCase())
                .description(description)
                .isPredefined(false)
                .build();
        StockUniverse saved = universeRepository.save(universe);
        log.info("Created custom universe: {}", saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/stocks")
    public ResponseEntity<UniverseStock> addStock(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (!universeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        String ticker = body.get("ticker");
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UniverseStock stock = UniverseStock.builder()
                .universeId(id)
                .ticker(ticker.toUpperCase())
                .companyName(body.get("companyName"))
                .sector(body.get("sector"))
                .exchange(body.getOrDefault("exchange", "NSE"))
                .build();
        UniverseStock saved = universeStockRepository.save(stock);
        log.info("Added ticker {} to universe {}", saved.getTicker(), id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}/stocks/{ticker}")
    @Transactional
    public ResponseEntity<Void> removeStock(
            @PathVariable UUID id,
            @PathVariable String ticker) {
        if (!universeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        universeStockRepository.deleteByUniverseIdAndTicker(id, ticker.toUpperCase());
        log.info("Removed ticker {} from universe {}", ticker, id);
        return ResponseEntity.noContent().build();
    }
}
