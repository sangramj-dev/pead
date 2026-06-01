-- PEAD Market Data Service
-- V2: stock_universes, universe_stocks, data_backfill_status

CREATE TABLE stock_universes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    is_predefined BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE universe_stocks (
    universe_id UUID REFERENCES stock_universes(id) ON DELETE CASCADE,
    ticker VARCHAR(20) NOT NULL,
    company_name VARCHAR(100),
    sector VARCHAR(50),
    exchange VARCHAR(5) DEFAULT 'NSE',
    added_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (universe_id, ticker)
);

CREATE TABLE data_backfill_status (
    ticker VARCHAR(20) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    last_backfill_date DATE,
    records_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (ticker, data_type)
);
