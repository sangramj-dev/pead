package org.pead.earnings.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.earnings.domain.EarningsAnnouncement;
import org.pead.earnings.scheduler.EarningsIngestionScheduler;
import org.pead.earnings.service.EarningsIngestionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/earnings")
@RequiredArgsConstructor
@Slf4j
public class EarningsController {

    private final EarningsIngestionService ingestionService;
    private final EarningsIngestionScheduler scheduler;

    @GetMapping("/date/{date}")
    public ResponseEntity<List<EarningsAnnouncement>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ingestionService.getEarningsByDate(date));
    }

    @GetMapping("/range")
    public ResponseEntity<List<EarningsAnnouncement>> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ingestionService.getEarningsByDateRange(startDate, endDate));
    }

    @PostMapping("/trigger/{date}")
    public ResponseEntity<Map<String, Object>> triggerIngestion(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Manual earnings ingestion trigger for date: {}", date);
        int count = scheduler.runIngestion(date);
        return ResponseEntity.ok(Map.of(
                "date", date.toString(),
                "recordsIngested", count,
                "status", "SUCCESS"
        ));
    }
}
