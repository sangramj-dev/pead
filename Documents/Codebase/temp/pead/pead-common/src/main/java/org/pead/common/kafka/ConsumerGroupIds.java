package org.pead.common.kafka;

/**
 * Central registry of all Kafka consumer group IDs.
 * Each consumer group must have a unique ID to track offsets independently.
 */
public final class ConsumerGroupIds {

    private ConsumerGroupIds() {}

    public static final String SCANNER_EARNINGS = "pead-scanner-earnings-cg";
    public static final String SCANNER_GAP = "pead-scanner-gap-cg";
    public static final String STRATEGY_VALIDATOR = "pead-strategy-validator-cg";
    public static final String SIGNAL_ENGINE = "pead-signal-engine-cg";
    public static final String PAPER_TRADING_ENTRY = "pead-paper-trading-entry-cg";
    public static final String PAPER_TRADING_MONITOR = "pead-paper-trading-monitor-cg";
    public static final String PORTFOLIO_TRACKER = "pead-portfolio-tracker-cg";
    public static final String RISK_ENGINE = "pead-risk-engine-cg";
    public static final String ANALYTICS = "pead-analytics-cg";
    public static final String BACKTESTING_REPLAY = "pead-backtesting-replay-cg";
    public static final String DLQ_PROCESSOR = "pead-dlq-processor-cg";
}
