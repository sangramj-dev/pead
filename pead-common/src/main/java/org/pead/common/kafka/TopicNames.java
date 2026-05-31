package org.pead.common.kafka;

/**
 * Central registry of all Kafka topic names used across the PEAD platform.
 * Using constants prevents typos and enables IDE refactoring support.
 * Topic naming convention: pead.{domain}.{event-type}.v{version}
 */
public final class TopicNames {

    private TopicNames() {}

    // =====================================================================
    // EARNINGS DOMAIN
    // =====================================================================
    public static final String EARNINGS_ANNOUNCEMENT = "pead.earnings.announcement.v1";
    public static final String EARNINGS_ESTIMATE = "pead.earnings.estimate.v1";

    // =====================================================================
    // MARKET DATA DOMAIN
    // =====================================================================
    public static final String MARKET_PRICE_BAR = "pead.market.price-bar.v1";
    public static final String MARKET_GAP_DETECTED = "pead.market.gap-detected.v1";
    public static final String MARKET_VOLUME_ANOMALY = "pead.market.volume-anomaly.v1";

    // =====================================================================
    // SCANNER DOMAIN
    // =====================================================================
    public static final String SCANNER_CANDIDATE = "pead.scanner.candidate.v1";

    // =====================================================================
    // STRATEGY DOMAIN
    // =====================================================================
    public static final String STRATEGY_VALIDATED_SIGNAL = "pead.strategy.validated-signal.v1";
    public static final String STRATEGY_REJECTED_CANDIDATE = "pead.strategy.rejected-candidate.v1";

    // =====================================================================
    // SIGNAL DOMAIN
    // =====================================================================
    public static final String SIGNAL_ENTRY = "pead.signal.entry-signal.v1";
    public static final String SIGNAL_APPROVED_ENTRY = "pead.signal.approved-entry.v1";
    public static final String SIGNAL_EXIT = "pead.signal.exit-signal.v1";

    // =====================================================================
    // RISK DOMAIN
    // =====================================================================
    public static final String RISK_VETO = "pead.risk.veto.v1";

    // =====================================================================
    // TRADING DOMAIN
    // =====================================================================
    public static final String TRADING_TRADE_EXECUTED = "pead.trading.trade-executed.v1";
    public static final String TRADING_POSITION_CLOSED = "pead.trading.position-closed.v1";
    public static final String TRADING_POSITION_UPDATED = "pead.trading.position-updated.v1";

    // =====================================================================
    // DEAD LETTER QUEUE
    // =====================================================================
    public static final String DLQ = "pead.dlq.v1";
}
