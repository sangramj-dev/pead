package org.pead.backtesting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.pead.backtesting.domain.BacktestEntity;
import org.pead.backtesting.domain.BacktestEquityCurveEntity;
import org.pead.backtesting.domain.BacktestTradeEntity;
import org.pead.backtesting.dto.BacktestRequest;
import org.pead.backtesting.engine.BacktestEngine;
import org.pead.backtesting.engine.model.EarningsEventData;
import org.pead.backtesting.engine.model.PriceBarData;
import org.pead.backtesting.repository.BacktestEquityCurveRepository;
import org.pead.backtesting.repository.BacktestRepository;
import org.pead.backtesting.repository.BacktestTradeRepository;
import org.pead.common.config.StrategyConfig;
import org.pead.common.model.BacktestResult;
import org.pead.common.model.EquityPoint;
import org.pead.common.model.TradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer that orchestrates backtest execution:
 * fetches data from sibling services, runs the engine, and persists results.
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private final BacktestRepository backtestRepository;
    private final BacktestTradeRepository tradeRepository;
    private final BacktestEquityCurveRepository equityCurveRepository;
    private final StrategyConfig defaultConfig;
    private final RestClient marketDataClient;
    private final RestClient earningsClient;
    private final ObjectMapper objectMapper;

    public BacktestService(
            BacktestRepository backtestRepository,
            BacktestTradeRepository tradeRepository,
            BacktestEquityCurveRepository equityCurveRepository,
            StrategyConfig defaultConfig,
            @Value("${pead.services.market-data-url}") String marketDataUrl,
            @Value("${pead.services.earnings-url}") String earningsUrl) {
        this.backtestRepository = backtestRepository;
        this.tradeRepository = tradeRepository;
        this.equityCurveRepository = equityCurveRepository;
        this.defaultConfig = defaultConfig;
        this.marketDataClient = RestClient.builder().baseUrl(marketDataUrl).build();
        this.earningsClient = RestClient.builder().baseUrl(earningsUrl).build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Submit a backtest for async execution.
     *
     * @param request backtest parameters
     * @return the backtest ID (results populated asynchronously)
     */
    public UUID submitBacktest(BacktestRequest request) {
        BacktestEntity entity = new BacktestEntity();
        entity.setStatus("RUNNING");
        try {
            entity.setParameters(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            entity.setParameters("{}");
        }
        entity = backtestRepository.save(entity);

        executeAsync(entity.getBacktestId(), request);
        return entity.getBacktestId();
    }

    @Async("backtestExecutor")
    public CompletableFuture<Void> executeAsync(UUID backtestId, BacktestRequest request) {
        try {
            log.info("Starting backtest execution: {}", backtestId);

            // Build effective config with overrides
            StrategyConfig effectiveConfig = buildEffectiveConfig(request);

            // Determine tickers to backtest
            List<String> tickers = resolveTickers(request);

            // Fetch data from sibling services
            Map<String, List<PriceBarData>> priceData = fetchPriceData(tickers, request.startDate(), request.endDate());
            List<EarningsEventData> earnings = fetchEarnings(tickers, request.startDate(), request.endDate());

            // Run the engine
            BacktestEngine engine = new BacktestEngine(effectiveConfig);
            BacktestResult result = engine.run(request.initialCapital(), earnings, priceData);

            // Persist results
            saveResults(backtestId, result);

            log.info("Backtest completed: {} — {} trades, win rate {:.2f}%",
                    backtestId, result.totalTrades(), result.winRate() * 100);

        } catch (Exception e) {
            log.error("Backtest failed: {}", backtestId, e);
            BacktestEntity entity = backtestRepository.findById(backtestId).orElse(null);
            if (entity != null) {
                entity.setStatus("FAILED");
                entity.setCompletedAt(LocalDateTime.now());
                backtestRepository.save(entity);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<BacktestEntity> getBacktest(UUID id) {
        return backtestRepository.findById(id);
    }

    public List<BacktestEntity> listBacktests() {
        return backtestRepository.findAll();
    }

    public List<BacktestTradeEntity> getTrades(UUID backtestId) {
        return tradeRepository.findByBacktestId(backtestId);
    }

    public List<BacktestEquityCurveEntity> getEquityCurve(UUID backtestId) {
        return equityCurveRepository.findByBacktestIdOrderByCurveDate(backtestId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private StrategyConfig buildEffectiveConfig(BacktestRequest req) {
        return new StrategyConfig(
                req.minEpsSurprisePct() != null ? req.minEpsSurprisePct() : defaultConfig.minEpsSurprisePct(),
                req.minRevenueSurprisePct() != null ? req.minRevenueSurprisePct() : defaultConfig.minRevenueSurprisePct(),
                req.minGapPct() != null ? req.minGapPct() : defaultConfig.minGapPct(),
                req.minRelativeVolume() != null ? req.minRelativeVolume() : defaultConfig.minRelativeVolume(),
                req.minPeadScore() != null ? req.minPeadScore() : defaultConfig.minPeadScore(),
                req.maxRiskPctPerTrade() != null ? req.maxRiskPctPerTrade() : defaultConfig.maxRiskPctPerTrade(),
                req.maxConcurrentPositions() != null ? req.maxConcurrentPositions() : defaultConfig.maxConcurrentPositions(),
                defaultConfig.dailyLossLimitPct(),
                defaultConfig.maxDrawdownPct(),
                req.maxPositionSizePct() != null ? req.maxPositionSizePct() : defaultConfig.maxPositionSizePct(),
                defaultConfig.maxSectorConcentrationPct(),
                req.profitTarget1R() != null ? req.profitTarget1R() : defaultConfig.profitTarget1R(),
                req.profitTarget2R() != null ? req.profitTarget2R() : defaultConfig.profitTarget2R(),
                req.enableShortSelling() != null ? req.enableShortSelling() : defaultConfig.enableShortSelling(),
                defaultConfig.excludedSectors()
        );
    }

    private List<String> resolveTickers(BacktestRequest request) {
        if (request.tickers() != null && !request.tickers().isEmpty()) {
            return request.tickers();
        }
        // If no explicit tickers, return a default universe
        // In production this would query the universe service
        return List.of();
    }

    private Map<String, List<PriceBarData>> fetchPriceData(
            List<String> tickers, LocalDate from, LocalDate to) {

        Map<String, List<PriceBarData>> result = new HashMap<>();
        for (String ticker : tickers) {
            try {
                List<PriceBarData> bars = marketDataClient.get()
                        .uri("/api/v1/market/bars?ticker={ticker}&from={from}&to={to}",
                                ticker, from, to)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<PriceBarData>>() {});
                if (bars != null && !bars.isEmpty()) {
                    result.put(ticker, bars);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch price data for {}: {}", ticker, e.getMessage());
            }
        }
        return result;
    }

    private List<EarningsEventData> fetchEarnings(
            List<String> tickers, LocalDate from, LocalDate to) {

        List<EarningsEventData> allEarnings = new ArrayList<>();
        for (String ticker : tickers) {
            try {
                List<EarningsEventData> events = earningsClient.get()
                        .uri("/api/v1/earnings?ticker={ticker}&from={from}&to={to}",
                                ticker, from, to)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<EarningsEventData>>() {});
                if (events != null) {
                    allEarnings.addAll(events);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch earnings for {}: {}", ticker, e.getMessage());
            }
        }
        return allEarnings;
    }

    private void saveResults(UUID backtestId, BacktestResult result) {
        // Update main entity
        BacktestEntity entity = backtestRepository.findById(backtestId).orElseThrow();
        entity.setStatus("COMPLETED");
        entity.setTotalTrades(result.totalTrades());
        entity.setWinningTrades(result.winningTrades());
        entity.setLosingTrades(result.losingTrades());
        entity.setWinRate(BigDecimal.valueOf(result.winRate()));
        entity.setProfitFactor(BigDecimal.valueOf(Math.min(result.profitFactor(), 999999.99)));
        entity.setSharpeRatio(BigDecimal.valueOf(result.sharpeRatio()));
        entity.setMaxDrawdownPct(BigDecimal.valueOf(result.maxDrawdownPct()));
        entity.setTotalPnl(result.totalPnl());
        entity.setFinalEquity(result.finalEquity());
        entity.setCagr(BigDecimal.valueOf(result.cagr()));
        entity.setAvgRMultiple(BigDecimal.valueOf(result.avgRMultiple()));
        entity.setExecutionTimeMs(result.executionTime().toMillis());
        entity.setCompletedAt(LocalDateTime.now());
        backtestRepository.save(entity);

        // Save trades
        List<BacktestTradeEntity> tradeEntities = result.trades().stream().map(t -> {
            BacktestTradeEntity te = new BacktestTradeEntity();
            te.setBacktestId(backtestId);
            te.setTicker(t.ticker());
            te.setDirection(t.direction().name());
            te.setEntryDate(t.entryDate());
            te.setExitDate(t.exitDate());
            te.setEntryPrice(t.entryPrice());
            te.setExitPrice(t.exitPrice());
            te.setQuantity(t.quantity());
            te.setPnl(t.pnl());
            te.setRMultiple(BigDecimal.valueOf(t.rMultiple()));
            te.setPeadScore(t.peadScore());
            te.setExitReason(t.exitReason());
            return te;
        }).toList();
        tradeRepository.saveAll(tradeEntities);

        // Save equity curve
        List<BacktestEquityCurveEntity> curveEntities = result.equityCurve().stream().map(ep -> {
            BacktestEquityCurveEntity ce = new BacktestEquityCurveEntity();
            ce.setBacktestId(backtestId);
            ce.setCurveDate(ep.date());
            ce.setEquity(ep.equity());
            ce.setDrawdownPct(BigDecimal.valueOf(ep.drawdownPct()));
            ce.setOpenPositions(ep.openPositions());
            return ce;
        }).toList();
        equityCurveRepository.saveAll(curveEntities);
    }
}
