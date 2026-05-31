-- PEAD Earnings Ingestion Service - Initial Schema
-- V1: Create earnings_announcements and outbox_events tables

CREATE TABLE IF NOT EXISTS earnings_announcements (
    id                      BIGSERIAL PRIMARY KEY,
    ticker                  VARCHAR(10) NOT NULL,
    announcement_date       DATE NOT NULL,
    announcement_time       VARCHAR(20),
    fiscal_quarter          VARCHAR(10),
    fiscal_year             INTEGER,
    eps_actual              DECIMAL(10,4),
    eps_estimate            DECIMAL(10,4),
    eps_surprise_pct        DECIMAL(8,4),
    eps_beat                BOOLEAN,
    revenue_actual          BIGINT,
    revenue_estimate        BIGINT,
    revenue_surprise_pct    DECIMAL(8,4),
    revenue_beat            BOOLEAN,
    both_beat               BOOLEAN DEFAULT FALSE,
    source                  VARCHAR(50) NOT NULL,
    raw_payload             JSONB,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uk_earnings_ticker_date_quarter UNIQUE (ticker, announcement_date, fiscal_quarter)
);

CREATE INDEX IF NOT EXISTS idx_ea_ticker ON earnings_announcements(ticker);
CREATE INDEX IF NOT EXISTS idx_ea_date ON earnings_announcements(announcement_date DESC);
CREATE INDEX IF NOT EXISTS idx_ea_both_beat ON earnings_announcements(both_beat, announcement_date)
    WHERE both_beat = TRUE;
CREATE INDEX IF NOT EXISTS idx_ea_created_at ON earnings_announcements(created_at DESC);

-- Outbox events table for transactional outbox pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    payload         BYTEA NOT NULL,
    partition_key   VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER DEFAULT 0,
    error_message   TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
