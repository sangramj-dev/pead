CREATE TABLE backtests (
    backtest_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parameters JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_trades INT,
    winning_trades INT,
    losing_trades INT,
    win_rate DECIMAL(5,2),
    profit_factor DECIMAL(8,2),
    sharpe_ratio DECIMAL(6,3),
    max_drawdown_pct DECIMAL(6,3),
    total_pnl DECIMAL(15,2),
    final_equity DECIMAL(15,2),
    cagr DECIMAL(6,3),
    avg_r_multiple DECIMAL(6,3),
    execution_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE TABLE backtest_trades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    backtest_id UUID REFERENCES backtests(backtest_id) ON DELETE CASCADE,
    ticker VARCHAR(20) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    entry_date DATE NOT NULL,
    exit_date DATE,
    entry_price DECIMAL(12,2) NOT NULL,
    exit_price DECIMAL(12,2),
    quantity INT NOT NULL,
    pnl DECIMAL(12,2),
    r_multiple DECIMAL(6,3),
    pead_score INT,
    exit_reason VARCHAR(30)
);

CREATE TABLE backtest_equity_curve (
    backtest_id UUID REFERENCES backtests(backtest_id) ON DELETE CASCADE,
    curve_date DATE NOT NULL,
    equity DECIMAL(15,2) NOT NULL,
    drawdown_pct DECIMAL(6,3),
    open_positions INT DEFAULT 0,
    PRIMARY KEY (backtest_id, curve_date)
);

CREATE INDEX idx_backtest_trades_backtest ON backtest_trades(backtest_id);
