package org.pead.marketdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.util.FinancialMath;
import org.pead.marketdata.domain.DailyIndicator;
import org.pead.marketdata.domain.PriceBar;
import org.pead.marketdata.repository.DailyIndicatorRepository;
import org.pead.marketdata.repository.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Computes technical indicators: EMA(20), EMA(50), SMA(200), ATR(14), relative volume.
 * EMA formula: EMA_t = price * k + EMA_{t-1} * (1 - k), k = 2 / (period + 1)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalIndicatorService {

    private final PriceBarRepository priceBarRepository;
    private final DailyIndicatorRepository indicatorRepository;

    @Transactional
    public DailyIndicator computeAndSave(String ticker, LocalDate date) {
        List<PriceBar> recentBars = priceBarRepository.findRecentDailyBars(ticker, 210);

        if (recentBars.isEmpty()) {
            log.warn("No price bars found for {} on {}", ticker, date);
            return null;
        }

        PriceBar today = recentBars.get(0);
        if (!today.getBarDate().equals(date)) {
            log.warn("Most recent bar for {} is {}, not {}", ticker, today.getBarDate(), date);
        }

        double closePrice = today.getClosePrice().doubleValue();

        // EMA 20 computation
        Double ema20 = computeEma(recentBars, 20);
        // EMA 50 computation
        Double ema50 = computeEma(recentBars, 50);
        // SMA 200
        Double sma200 = recentBars.size() >= 200
                ? recentBars.stream().limit(200).mapToDouble(b -> b.getClosePrice().doubleValue()).average().orElse(0.0)
                : null;

        // ATR 14
        Double atr14 = computeAtr(recentBars, 14);

        // Relative volume: today volume / 20-day average volume
        long todayVolume = today.getVolume();
        long avgVolume20d = computeAvgVolume(recentBars, 20);
        double relVolume = avgVolume20d > 0 ? FinancialMath.relativeVolume(todayVolume, avgVolume20d) : 0.0;

        // % from 52-week high
        OptionalDouble weekHigh52 = recentBars.stream().limit(252)
                .mapToDouble(b -> b.getHighPrice().doubleValue()).max();
        Double pctFromHigh = weekHigh52.isPresent() && weekHigh52.getAsDouble() > 0
                ? (weekHigh52.getAsDouble() - closePrice) / weekHigh52.getAsDouble() * 100.0
                : null;

        DailyIndicator indicator = DailyIndicator.builder()
                .ticker(ticker)
                .indicatorDate(date)
                .ema20(ema20 != null ? BigDecimal.valueOf(ema20).setScale(4, RoundingMode.HALF_UP) : null)
                .ema50(ema50 != null ? BigDecimal.valueOf(ema50).setScale(4, RoundingMode.HALF_UP) : null)
                .sma200(sma200 != null ? BigDecimal.valueOf(sma200).setScale(4, RoundingMode.HALF_UP) : null)
                .atr14(atr14 != null ? BigDecimal.valueOf(atr14).setScale(4, RoundingMode.HALF_UP) : null)
                .relVolume(BigDecimal.valueOf(relVolume).setScale(4, RoundingMode.HALF_UP))
                .avgVolume20d(avgVolume20d)
                .closePrice(today.getClosePrice())
                .pctFromHigh(pctFromHigh != null ? BigDecimal.valueOf(pctFromHigh).setScale(4, RoundingMode.HALF_UP) : null)
                .build();

        return indicatorRepository.save(indicator);
    }

    /**
     * Computes EMA for the given period.
     * Bars are ordered DESC (most recent first), so we reverse for calculation.
     */
    private Double computeEma(List<PriceBar> barsDesc, int period) {
        if (barsDesc.size() < period) return null;

        // Take the last N bars and reverse to chronological order
        List<PriceBar> bars = barsDesc.stream().limit(period * 3L).toList();
        List<Double> closes = bars.stream()
                .map(b -> b.getClosePrice().doubleValue())
                .toList()
                .reversed();

        // Seed with SMA of first `period` bars
        double seed = closes.stream().limit(period).mapToDouble(Double::doubleValue).average().orElse(0.0);
        double ema = seed;
        for (int i = period; i < closes.size(); i++) {
            ema = FinancialMath.ema(closes.get(i), ema, period);
        }
        return ema;
    }

    /**
     * Computes ATR(14): Average True Range over 14 periods.
     */
    private Double computeAtr(List<PriceBar> barsDesc, int period) {
        if (barsDesc.size() < period + 1) return null;

        double atrSum = 0.0;
        for (int i = 0; i < period; i++) {
            PriceBar current = barsDesc.get(i);
            PriceBar prev = barsDesc.get(i + 1);
            double high = current.getHighPrice().doubleValue();
            double low = current.getLowPrice().doubleValue();
            double prevClose = prev.getClosePrice().doubleValue();
            double trueRange = Math.max(high - low,
                              Math.max(Math.abs(high - prevClose),
                                       Math.abs(low - prevClose)));
            atrSum += trueRange;
        }
        return atrSum / period;
    }

    private long computeAvgVolume(List<PriceBar> barsDesc, int days) {
        if (barsDesc.size() < 2) return 0L;
        // Skip today (index 0), use previous days
        return (long) barsDesc.stream().skip(1).limit(days)
                .mapToLong(PriceBar::getVolume)
                .average()
                .orElse(0.0);
    }
}
