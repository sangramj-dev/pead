-- PEAD Market Data Service - Initial Schema
-- V1: price_bars, daily_indicators, gap_events

CREATE TABLE IF NOT EXISTS price_bars (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(10) NOT NULL,
    bar_date        DATE NOT NULL,
    timeframe       VARCHAR(10) NOT NULL DEFAULT '1D',
    open_price      DECIMAL(12,4) NOT NULL,
    high_price      DECIMAL(12,4) NOT NULL,
    low_price       DECIMAL(12,4) NOT NULL,
    close_price     DECIMAL(12,4) NOT NULL,
    volume          BIGINT NOT NULL,
    vwap            DECIMAL(12,4),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uk_price_bar UNIQUE (ticker, bar_date, timeframe)
);

CREATE INDEX IF NOT EXISTS idx_pb_ticker_date ON price_bars(ticker, bar_date DESC);
CREATE INDEX IF NOT EXISTS idx_pb_date ON price_bars(bar_date DESC);

CREATE TABLE IF NOT EXISTS daily_indicators (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(10) NOT NULL,
    indicator_date  DATE NOT NULL,
    ema_20          DECIMAL(12,4),
    ema_50          DECIMAL(12,4),
    sma_200         DECIMAL(12,4),
    atr_14          DECIMAL(12,4),
    rel_volume      DECIMAL(8,4),
    avg_volume_20d  BIGINT,
    close_price     DECIMAL(12,4),
    pct_from_high   DECIMAL(8,4),
    above_ema20     BOOLEAN,
    above_ema50     BOOLEAN,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uk_indicator_ticker_date UNIQUE (ticker, indicator_date)
);

CREATE INDEX IF NOT EXISTS idx_di_ticker_date ON daily_indicators(ticker, indicator_date DESC);

CREATE TABLE IF NOT EXISTS gap_events (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(10) NOT NULL,
    gap_date        DATE NOT NULL,
    prev_close      DECIMAL(12,4),
    open_price      DECIMAL(12,4),
    gap_pct         DECIMAL(8,4),
    gap_direction   VARCHAR(10),
    rel_volume      DECIMAL(8,4),
    earnings_related BOOLEAN DEFAULT FALSE,
    earnings_date   DATE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uk_gap_ticker_date UNIQUE (ticker, gap_date)
);

CREATE INDEX IF NOT EXISTS idx_gap_ticker_date ON gap_events(ticker, gap_date DESC);
CREATE INDEX IF NOT EXISTS idx_gap_earnings ON gap_events(earnings_related, gap_date) WHERE earnings_related = TRUE;
