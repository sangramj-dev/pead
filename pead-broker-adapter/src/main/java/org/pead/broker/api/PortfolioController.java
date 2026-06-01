package org.pead.broker.api;

import lombok.RequiredArgsConstructor;
import org.pead.broker.adapter.BrokerAdapter;
import org.pead.broker.config.BrokerConfig;
import org.pead.broker.domain.PortfolioEquityEntity;
import org.pead.broker.domain.PositionEntity;
import org.pead.broker.domain.TradeHistoryEntity;
import org.pead.broker.model.Order;
import org.pead.broker.model.Position;
import org.pead.broker.repository.PortfolioEquityRepository;
import org.pead.broker.repository.PositionRepository;
import org.pead.broker.repository.TradeHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final BrokerAdapter brokerAdapter;
    private final PositionRepository positionRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PortfolioEquityRepository portfolioEquityRepository;
    private final BrokerConfig brokerConfig;

    @GetMapping("/positions")
    public ResponseEntity<List<Position>> getOpenPositions() {
        List<Position> positions = brokerAdapter.getPositions();
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeHistoryEntity>> getTradeHistory() {
        List<TradeHistoryEntity> trades = tradeHistoryRepository.findAllByOrderByExitDateDesc();
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
        List<PositionEntity> openPositions = positionRepository.findByStatus("OPEN");
        List<TradeHistoryEntity> allTrades = tradeHistoryRepository.findAll();

        // Calculate total unrealised P&L
        BigDecimal unrealisedPnl = openPositions.stream()
                .map(PositionEntity::getUnrealisedPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total realised P&L
        BigDecimal realisedPnl = allTrades.stream()
                .map(TradeHistoryEntity::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate win rate
        long totalTrades = allTrades.size();
        long winningTrades = allTrades.stream()
                .filter(t -> t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal winRate = totalTrades > 0
                ? BigDecimal.valueOf(winningTrades)
                        .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Calculate total equity (initial capital + realised + unrealised)
        BigDecimal totalEquity = brokerConfig.getInitialCapital()
                .add(realisedPnl)
                .add(unrealisedPnl);

        // Calculate cash (initial capital + realised - positions value)
        BigDecimal positionsValue = openPositions.stream()
                .map(p -> p.getEntryPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = brokerConfig.getInitialCapital()
                .add(realisedPnl)
                .subtract(positionsValue);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEquity", totalEquity.setScale(2, RoundingMode.HALF_UP));
        summary.put("cash", cash.setScale(2, RoundingMode.HALF_UP));
        summary.put("unrealisedPnl", unrealisedPnl.setScale(2, RoundingMode.HALF_UP));
        summary.put("realisedPnl", realisedPnl.setScale(2, RoundingMode.HALF_UP));
        summary.put("openPositionsCount", openPositions.size());
        summary.put("totalTrades", totalTrades);
        summary.put("winRate", winRate.setScale(2, RoundingMode.HALF_UP));

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/equity")
    public ResponseEntity<List<PortfolioEquityEntity>> getEquityCurve() {
        List<PortfolioEquityEntity> equity = portfolioEquityRepository.findAllByOrderByEquityDateAsc();
        return ResponseEntity.ok(equity);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getRecentOrders() {
        List<Order> orders = brokerAdapter.getOrders();
        return ResponseEntity.ok(orders);
    }
}
