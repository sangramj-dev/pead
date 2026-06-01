package org.pead.earnings.client;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.pead.earnings.client.dto.EarningsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Scrapes quarterly results from Screener.in for Indian listed companies (NSE/BSE).
 * <p>
 * Since Screener.in does not provide analyst estimates, epsEstimate and revenueEstimate
 * are left null. The backtesting engine must calculate surprise using YoY EPS growth
 * instead of the standard beat/miss calculation.
 */
@Component
@Slf4j
public class ScreenerClient {

    private static final String BASE_URL = "https://www.screener.in";
    private static final String COMPANY_PATH = "/company/%s/consolidated/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    // Matches headers like "Mar 2024", "Jun 2023", "Sep 2022", "Dec 2021"
    private static final DateTimeFormatter QUARTER_DATE_FORMATTER =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM yyyy")
                    .toFormatter(Locale.ENGLISH);

    @Value("${pead.data-sources.screener.request-delay-ms:1000}")
    private long requestDelayMs;

    /**
     * Fetches quarterly results for the given NSE/BSE ticker from Screener.in.
     *
     * @param ticker the stock ticker symbol (e.g. "TATAELXSI", "INFY")
     * @return list of {@link EarningsDto} — one per quarter found; empty list on any failure
     */
    public List<EarningsDto> getQuarterlyResults(String ticker) {
        String url = BASE_URL + String.format(COMPANY_PATH, ticker.toUpperCase());
        log.info("Scraping Screener.in for ticker={} url={}", ticker, url);

        try {
            applyRateLimit();

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .get();

            return parseQuartersTable(ticker, doc);

        } catch (Exception e) {
            log.error("Failed to scrape Screener.in for ticker={}: {}", ticker, e.getMessage());
            return fallbackResults(ticker, e);
        }
    }

    /**
     * Parses the #quarters table from the Screener.in company page.
     */
    private List<EarningsDto> parseQuartersTable(String ticker, Document doc) {
        Element quartersSection = doc.getElementById("quarters");
        if (quartersSection == null) {
            log.warn("No #quarters section found for ticker={}", ticker);
            return Collections.emptyList();
        }

        Element table = quartersSection.selectFirst("table");
        if (table == null) {
            log.warn("No table inside #quarters for ticker={}", ticker);
            return Collections.emptyList();
        }

        // First row = header row with quarter labels
        Element headerRow = table.selectFirst("thead tr");
        if (headerRow == null) {
            headerRow = table.selectFirst("tr");
        }
        if (headerRow == null) {
            log.warn("No header row found in #quarters table for ticker={}", ticker);
            return Collections.emptyList();
        }

        Elements headerCells = headerRow.select("th");
        // headerCells[0] is the row label column (e.g. ""), columns 1..N are quarters
        List<String> quarterHeaders = new ArrayList<>();
        for (int i = 1; i < headerCells.size(); i++) {
            quarterHeaders.add(headerCells.get(i).text().trim());
        }

        if (quarterHeaders.isEmpty()) {
            log.warn("No quarter columns found for ticker={}", ticker);
            return Collections.emptyList();
        }

        // Collect data rows by label
        Elements dataRows = table.select("tbody tr");
        BigDecimal[] salesRow = new BigDecimal[quarterHeaders.size()];
        BigDecimal[] netProfitRow = new BigDecimal[quarterHeaders.size()];
        BigDecimal[] epsRow = new BigDecimal[quarterHeaders.size()];

        for (Element row : dataRows) {
            Elements cells = row.select("td");
            if (cells.isEmpty()) continue;

            String rowLabel = cells.first().text().trim().toLowerCase();
            boolean isSales = rowLabel.contains("sales") || rowLabel.contains("revenue");
            boolean isNetProfit = rowLabel.contains("net profit");
            boolean isEps = rowLabel.equals("eps") || rowLabel.startsWith("eps ");

            if (!isSales && !isNetProfit && !isEps) continue;

            for (int i = 1; i < cells.size() && (i - 1) < quarterHeaders.size(); i++) {
                String rawValue = cells.get(i).text().trim().replace(",", "");
                BigDecimal parsed = parseBigDecimal(rawValue);
                int idx = i - 1;
                if (isSales && salesRow[idx] == null)       salesRow[idx] = parsed;
                if (isNetProfit && netProfitRow[idx] == null) netProfitRow[idx] = parsed;
                if (isEps && epsRow[idx] == null)           epsRow[idx] = parsed;
            }
        }

        List<EarningsDto> results = new ArrayList<>();
        for (int i = 0; i < quarterHeaders.size(); i++) {
            String quarterHeader = quarterHeaders.get(i);
            LocalDate announcementDate = parseQuarterDate(quarterHeader);
            if (announcementDate == null) {
                log.debug("Could not parse quarter date from '{}', skipping", quarterHeader);
                continue;
            }

            BigDecimal epsActual = epsRow[i];
            // Revenue on Screener is in Crores; convert to absolute value (1 Crore = 10,000,000)
            Long revenueActual = salesRow[i] != null
                    ? salesRow[i].multiply(BigDecimal.valueOf(10_000_000L)).longValue()
                    : null;

            EarningsDto dto = EarningsDto.builder()
                    .ticker(ticker.toUpperCase())
                    .announcementDate(announcementDate)
                    .fiscalQuarter(quarterHeader)
                    .epsActual(epsActual)
                    .epsEstimate(null)        // Screener.in has no analyst estimates
                    .revenueActual(revenueActual)
                    .revenueEstimate(null)    // Screener.in has no analyst estimates
                    .source("SCREENER")
                    .rawPayload(null)
                    .build();

            results.add(dto);
            log.debug("Scraped quarter={} ticker={} eps={} revenue={}",
                    quarterHeader, ticker, epsActual, revenueActual);
        }

        log.info("Scraped {} quarters from Screener.in for ticker={}", results.size(), ticker);
        return results;
    }

    /**
     * Parses a quarter header string like "Mar 2024" into the last day of that month.
     */
    private LocalDate parseQuarterDate(String header) {
        try {
            // e.g. "Mar 2024" -> 2024-03-01, then end of month
            LocalDate firstOfMonth = LocalDate.parse("01 " + header,
                    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
            return firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
        } catch (Exception e) {
            log.debug("Could not parse quarter header '{}': {}", header, e.getMessage());
            return null;
        }
    }

    /**
     * Parses a numeric string, returning null for blanks or non-numeric values.
     */
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Applies the configured rate-limit delay between requests.
     */
    private void applyRateLimit() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Fallback method for circuit-breaker pattern — returns an empty list.
     */
    public List<EarningsDto> fallbackResults(String ticker, Exception ex) {
        log.warn("Screener.in fallback triggered for ticker={}: {}", ticker,
                ex != null ? ex.getMessage() : "unknown error");
        return Collections.emptyList();
    }
}
