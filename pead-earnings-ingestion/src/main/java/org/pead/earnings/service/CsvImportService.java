package org.pead.earnings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.earnings.domain.EarningsAnnouncement;
import org.pead.earnings.repository.EarningsAnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Imports earnings announcements from a CSV file.
 *
 * <p>Expected CSV format (first line is the header, subsequent lines are data):
 * <pre>
 * ticker,date,quarter,eps_actual,eps_estimate,revenue_actual,revenue_estimate,source
 * TATAELXSI,2024-01-15,Q3FY24,82.4,71.0,3450000000,3200000000,manual
 * </pre>
 *
 * <p>Rows with parse errors are skipped and logged; processing continues for all remaining rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int SCALE = 4;

    private final EarningsAnnouncementRepository repository;

    /**
     * Parses the given CSV file and persists each valid row as an {@link EarningsAnnouncement}.
     * Duplicate rows (same ticker + date + quarter) are silently skipped via the repository's
     * unique-constraint check.
     *
     * @param file the uploaded CSV file
     * @return count of records successfully imported (new inserts only)
     */
    public int importCsv(MultipartFile file) {
        int imported = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    // Skip header row
                    log.debug("CSV header: {}", line);
                    continue;
                }
                if (line.isBlank()) continue;

                try {
                    EarningsAnnouncement announcement = parseLine(line, lineNumber);
                    if (announcement == null) continue;

                    boolean alreadyExists = repository.existsByTickerAndAnnouncementDateAndFiscalQuarter(
                            announcement.getTicker(),
                            announcement.getAnnouncementDate(),
                            announcement.getFiscalQuarter());

                    if (alreadyExists) {
                        log.debug("Duplicate skipped at line {}: ticker={} date={} quarter={}",
                                lineNumber,
                                announcement.getTicker(),
                                announcement.getAnnouncementDate(),
                                announcement.getFiscalQuarter());
                        continue;
                    }

                    repository.save(announcement);
                    imported++;

                } catch (Exception e) {
                    log.error("Error importing CSV row at line {}: {} — {}", lineNumber, line, e.getMessage());
                    // Continue processing remaining rows
                }
            }

        } catch (Exception e) {
            log.error("Failed to read CSV file '{}': {}", file.getOriginalFilename(), e.getMessage());
        }

        log.info("CSV import complete: file={} totalLines={} imported={}",
                file.getOriginalFilename(), lineNumber - 1, imported);
        return imported;
    }

    /**
     * Parses a single CSV line into an {@link EarningsAnnouncement}.
     *
     * @param line       the raw CSV line
     * @param lineNumber used for logging only
     * @return the constructed entity, or {@code null} if required fields are missing/invalid
     */
    private EarningsAnnouncement parseLine(String line, int lineNumber) {
        String[] parts = line.split(",", -1);
        if (parts.length < 8) {
            log.warn("Skipping line {} — expected 8 columns, found {}: {}", lineNumber, parts.length, line);
            return null;
        }

        String ticker       = trimOrNull(parts[0]);
        String dateStr      = trimOrNull(parts[1]);
        String quarter      = trimOrNull(parts[2]);
        String epsActualStr = trimOrNull(parts[3]);
        String epsEstStr    = trimOrNull(parts[4]);
        String revActualStr = trimOrNull(parts[5]);
        String revEstStr    = trimOrNull(parts[6]);
        String source       = trimOrNull(parts[7]);

        if (ticker == null || dateStr == null || quarter == null) {
            log.warn("Skipping line {} — required fields (ticker, date, quarter) are blank", lineNumber);
            return null;
        }

        LocalDate announcementDate;
        try {
            announcementDate = LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Skipping line {} — invalid date '{}': {}", lineNumber, dateStr, e.getMessage());
            return null;
        }

        BigDecimal epsActual   = parseBigDecimal(epsActualStr);
        BigDecimal epsEstimate = parseBigDecimal(epsEstStr);
        Long revenueActual     = parseLong(revActualStr);
        Long revenueEstimate   = parseLong(revEstStr);

        BigDecimal epsSurprisePct     = calculateSurprisePct(epsActual, epsEstimate);
        BigDecimal revenueSurprisePct = calculateRevSurprisePct(revenueActual, revenueEstimate);

        Boolean epsBeat     = (epsSurprisePct != null) ? epsSurprisePct.compareTo(BigDecimal.ZERO) > 0 : null;
        Boolean revenueBeat = (revenueSurprisePct != null) ? revenueSurprisePct.compareTo(BigDecimal.ZERO) > 0 : null;
        Boolean bothBeat    = Boolean.TRUE.equals(epsBeat) && Boolean.TRUE.equals(revenueBeat);

        return EarningsAnnouncement.builder()
                .ticker(ticker.toUpperCase())
                .announcementDate(announcementDate)
                .fiscalQuarter(quarter)
                .epsActual(epsActual)
                .epsEstimate(epsEstimate)
                .epsSurprisePct(epsSurprisePct)
                .epsBeat(epsBeat)
                .revenueActual(revenueActual)
                .revenueEstimate(revenueEstimate)
                .revenueSurprisePct(revenueSurprisePct)
                .revenueBeat(revenueBeat)
                .bothBeat(bothBeat)
                .source(source != null ? source : "CSV")
                .build();
    }

    // --- Calculation helpers ---

    private BigDecimal calculateSurprisePct(BigDecimal actual, BigDecimal estimate) {
        if (actual == null || estimate == null) return null;
        if (estimate.compareTo(BigDecimal.ZERO) == 0) return null;
        return actual.subtract(estimate)
                .divide(estimate.abs(), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateRevSurprisePct(Long actual, Long estimate) {
        if (actual == null || estimate == null || estimate == 0L) return null;
        BigDecimal bdActual   = BigDecimal.valueOf(actual);
        BigDecimal bdEstimate = BigDecimal.valueOf(estimate);
        return bdActual.subtract(bdEstimate)
                .divide(bdEstimate.abs(), SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // --- Parse helpers ---

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null) return null;
        try {
            // Handle values like "3.45E9" from scientific notation
            return new BigDecimal(value).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
