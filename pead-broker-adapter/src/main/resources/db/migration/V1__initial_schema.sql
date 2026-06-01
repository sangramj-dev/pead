CREATE TABLE broker_orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id VARCHAR(50),
    ticker VARCHAR(20) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'LIMIT',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    quantity INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stop_loss DECIMAL(12,2),
    target1 DECIMAL(12,2),
    target2 DECIMAL(12,2),
    filled_price DECIMAL(12,2),
    filled_quantity INT,
    broker_order_id VARCHAR(50),
    broker_type VARCHAR(20) NOT NULL DEFAULT 'PAPER',
    pead_score INT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    filled_at TIMESTAMP
);

CREATE TABLE positions (
    position_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID REFERENCES broker_orders(order_id),
    ticker VARCHAR(20) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    quantity INT NOT NULL,
    entry_price DECIMAL(12,2) NOT NULL,
    current_price DECIMAL(12,2),
    stop_loss DECIMAL(12,2),
    target1 DECIMAL(12,2),
    target2 DECIMAL(12,2),
    target1_hit BOOLEAN DEFAULT FALSE,
    unrealised_pnl DECIMAL(12,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP DEFAULT NOW(),
    closed_at TIMESTAMP,
    exit_price DECIMAL(12,2),
    exit_reason VARCHAR(30),
    realised_pnl DECIMAL(12,2)
);

CREATE TABLE trade_history (
    trade_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id UUID REFERENCES positions(position_id),
    ticker VARCHAR(20) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    quantity INT NOT NULL,
    entry_price DECIMAL(12,2) NOT NULL,
    exit_price DECIMAL(12,2) NOT NULL,
    pnl DECIMAL(12,2) NOT NULL,
    r_multiple DECIMAL(6,3),
    pead_score INT,
    entry_date TIMESTAMP NOT NULL,
    exit_date TIMESTAMP NOT NULL,
    exit_reason VARCHAR(30),
    broker_type VARCHAR(20)
);

CREATE TABLE portfolio_equity (
    id BIGSERIAL PRIMARY KEY,
    equity_date DATE NOT NULL UNIQUE,
    total_equity DECIMAL(15,2) NOT NULL,
    cash DECIMAL(15,2) NOT NULL,
    positions_value DECIMAL(15,2) NOT NULL,
    daily_pnl DECIMAL(12,2),
    drawdown_pct DECIMAL(6,3)
);

CREATE INDEX idx_orders_status ON broker_orders(status);
CREATE INDEX idx_positions_status ON positions(status);
CREATE INDEX idx_trades_ticker ON trade_history(ticker);
CREATE INDEX idx_equity_date ON portfolio_equity(equity_date);
