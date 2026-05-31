CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Validated signals: candidates that passed full PEAD scoring
CREATE TABLE IF NOT EXISTS validated_signals (
    signal_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticker              VARCHAR(20)     NOT NULL,
    direction           VARCHAR(10)     NOT NULL,
    signal_date         DATE            NOT NULL,
    earnings_date       DATE,
    pead_score          INTEGER         NOT NULL,
    entry_price         NUMERIC(12, 4)  NOT NULL,
    stop_loss           NUMERIC(12, 4)  NOT NULL,
    target_1            NUMERIC(12, 4)  NOT NULL,
    target_2            NUMERIC(12, 4)  NOT NULL,
    risk_reward_ratio   NUMERIC(8, 4)   NOT NULL,
    eps_surprise_pct    DOUBLE PRECISION,
    revenue_surprise_pct DOUBLE PRECISION,
    gap_pct             DOUBLE PRECISION,
    rel_volume          DOUBLE PRECISION,
    ema_20              NUMERIC(12, 4),
    ema_50              NUMERIC(12, 4),
    above_ema20         BOOLEAN,
    above_ema50         BOOLEAN,
    close_near_high     BOOLEAN,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    expiry_date         DATE,
    correlation_id      VARCHAR(100),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vs_direction CHECK (direction IN ('LONG', 'SHORT')),
    CONSTRAINT chk_vs_status    CHECK (status IN ('ACTIVE', 'TRIGGERED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_vs_ticker      ON validated_signals (ticker);
CREATE INDEX IF NOT EXISTS idx_vs_signal_date ON validated_signals (signal_date);
CREATE INDEX IF NOT EXISTS idx_vs_status      ON validated_signals (status);
CREATE INDEX IF NOT EXISTS idx_vs_expiry      ON validated_signals (expiry_date) WHERE status = 'ACTIVE';

-- Per-component PEAD score breakdown
CREATE TABLE IF NOT EXISTS scoring_details (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    signal_id       UUID            NOT NULL REFERENCES validated_signals(signal_id) ON DELETE CASCADE,
    component       VARCHAR(50)     NOT NULL,
    raw_value       DOUBLE PRECISION,
    score           INTEGER         NOT NULL,
    weight          INTEGER         NOT NULL,
    weighted_score  INTEGER         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sd_signal_id ON scoring_details (signal_id);

-- Candidates that failed PEAD validation
CREATE TABLE IF NOT EXISTS rejected_candidates (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticker              VARCHAR(20)  NOT NULL,
    scan_date           DATE         NOT NULL,
    rejection_reasons   JSONB        NOT NULL DEFAULT '[]',
    raw_metrics         JSONB        NOT NULL DEFAULT '{}',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rc_ticker    ON rejected_candidates (ticker);
CREATE INDEX IF NOT EXISTS idx_rc_scan_date ON rejected_candidates (scan_date);
