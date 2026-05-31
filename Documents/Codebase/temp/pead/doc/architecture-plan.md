# PEAD Swing Trading Platform - Architecture & Implementation Plan

## Context

Greenfield project. Current state: single Maven module (`pead`, Java 17) with a placeholder `Main.java` and no dependencies. Goal is to build a production-grade, fully automated PEAD (Post Earnings Announcement Drift) swing trading platform using event-driven microservices. Paper trading first; live broker execution later.

---

## Phase Structure

| Phase | Scope | Services |
|---|---|---|
| 1 | Earnings + market data ingestion, scanner, strategy validation | pead-earnings-ingestion, pead-market-data, pead-scanner, pead-strategy-validator |
| 2 | Signal engine, paper trading, portfolio tracking, API gateway | pead-signal-engine, pead-paper-trading, pead-portfolio-tracker, pead-api-gateway |
| 3 | Backtesting, risk engine, analytics dashboard | pead-backtesting, pead-risk-engine, pead-analytics |
| 4 | Live broker integration, Kubernetes, full CI/CD | pead-broker-adapter, K8s manifests, Helm charts |

---

## 1. Repository Structure (Multi-Module Maven)

```
PEAD/
├── pom.xml                           ← Parent reactor POM (replace current)
├── docker-compose.yml                ← Local infra (Kafka, Postgres, Redis, Prometheus, Grafana)
├── docker-compose-services.yml       ← Local microservices (Phase 2+)
├── infrastructure/
│   ├── kafka/
│   ├── postgres/init.sql
│   └── grafana/dashboards/
├── scripts/init-topics.sh            ← Create all Kafka topics
├── k8s/                              ← Kubernetes manifests (Phase 4)
│
├── pead-common/                      ← Shared library (not a Spring Boot app)
├── pead-earnings-ingestion/          ← Phase 1, port 8081
├── pead-market-data/                 ← Phase 1, port 8082
├── pead-scanner/                     ← Phase 1, port 8083
├── pead-strategy-validator/          ← Phase 1, port 8084
├── pead-signal-engine/               ← Phase 2, port 8085
├── pead-paper-trading/               ← Phase 2, port 8086
├── pead-portfolio-tracker/           ← Phase 2, port 8087
├── pead-risk-engine/                 ← Phase 3, port 8089
├── pead-backtesting/                 ← Phase 3, port 8088
├── pead-analytics/                   ← Phase 3, port 8090
└── pead-api-gateway/                 ← Phase 2+, port 8080
```

---

## 2. Parent POM Key Versions

Replace `PEAD/pom.xml` entirely.

```xml
<groupId>org.pead</groupId>
<artifactId>pead-parent</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<java.version>21</java.version>
<spring-boot.version>3.3.4</spring-boot.version>
<kafka.version>3.7.0</kafka.version>
<postgresql.version>42.7.3</postgresql.version>
<flyway.version>10.15.0</flyway.version>
<micrometer.version>1.13.0</micrometer.version>
<resilience4j.version>2.2.0</resilience4j.version>
<mapstruct.version>1.6.0</mapstruct.version>
<lombok.version>1.18.34</lombok.version>
<testcontainers.version>1.20.1</testcontainers.version>
```

**Build order declared in `<modules>`:**
pead-common → (all services in dependency order)

---

## 3. pead-common (shared library)

**File:** `pead-common/src/main/java/org/pead/common/`

Key classes to create:
- `kafka/TopicNames.java` — all 16 Kafka topic name constants
- `kafka/ConsumerGroupIds.java` — all consumer group ID constants
- `exception/PeadBaseException.java` + subclasses
- `util/DateUtils.java`, `FinancialMath.java`
- `config/StrategyConfig.java` — `@ConfigurationProperties(prefix="pead.strategy")` record with all thresholds

---

## 4. Event Contracts

All inter-service Kafka event classes live in `pead-common/src/main/java/org/pead/common/event/`.
Serialization: Jackson JSON (`ObjectMapper.writeValueAsBytes`) stored as `BYTEA` in the outbox,
published via `KafkaTemplate<String, byte[]>` with `ByteArraySerializer`.
Schema evolution is handled by `@JsonIgnoreProperties(ignoreUnknown = true)` on consumers.

| Class | Published By |
|---|---|
| `EarningsAnnouncementEvent` | pead-earnings-ingestion |
| `GapDetectedEvent` | pead-market-data |
| `PriceBarEvent` | pead-market-data |
| `CandidateSymbolEvent` | pead-scanner |
| `ValidatedSignalEvent` | pead-strategy-validator |
| `EntrySignalEvent` | pead-signal-engine |
| `TradeExecutedEvent` | pead-paper-trading |
| `PositionClosedEvent` | pead-paper-trading |
| `RiskVetoEvent` | pead-risk-engine |

All events include: `eventId` (String), `correlationId` (String), `eventTimestamp` (long, epoch millis).

---

## 5. Kafka Topics

**Topic naming:** `pead.{domain}.{event-type}.v{version}`

| Topic | Partitions | Key | Primary Consumers |
|---|---|---|---|
| `pead.earnings.announcement.v1` | 8 | ticker | scanner, strategy-validator |
| `pead.earnings.estimate.v1` | 4 | ticker | scanner |
| `pead.market.price-bar.v1` | 16 | ticker | scanner, signal-engine |
| `pead.market.gap-detected.v1` | 8 | ticker | scanner |
| `pead.market.volume-anomaly.v1` | 8 | ticker | scanner |
| `pead.scanner.candidate.v1` | 8 | ticker | strategy-validator |
| `pead.strategy.validated-signal.v1` | 8 | ticker | signal-engine, risk-engine |
| `pead.strategy.rejected-candidate.v1` | 4 | ticker | analytics |
| `pead.signal.entry-signal.v1` | 8 | ticker | paper-trading, risk-engine |
| `pead.signal.approved-entry.v1` | 8 | ticker | paper-trading |
| `pead.signal.exit-signal.v1` | 8 | ticker | paper-trading |
| `pead.risk.veto.v1` | 4 | ticker | signal-engine, paper-trading |
| `pead.trading.trade-executed.v1` | 8 | trade-id | portfolio-tracker, risk-engine |
| `pead.trading.position-closed.v1` | 8 | trade-id | portfolio-tracker, analytics |
| `pead.trading.position-updated.v1` | 8 | trade-id | portfolio-tracker |
| `pead.dlq.v1` | 4 | service-name | ops |

**Partitioning rationale:** Ticker symbol as key ensures all events for one symbol go to the same partition — critical for Kafka Streams KStream-KStream joins and stateful windowed processing.

**Consumer group IDs** (defined in `ConsumerGroupIds.java`):
`pead-scanner-earnings-cg`, `pead-scanner-gap-cg`, `pead-strategy-validator-cg`, `pead-signal-engine-cg`, `pead-paper-trading-entry-cg`, `pead-paper-trading-monitor-cg`, `pead-portfolio-tracker-cg`, `pead-risk-engine-cg`, `pead-analytics-cg`, `pead-backtesting-replay-cg`

---

## 6. Microservices Breakdown

### pead-earnings-ingestion (Phase 1)

Responsibilities:
- Poll Polygon.io / FMP / Alpha Vantage for earnings calendars and actuals
- Calculate EPS surprise % and revenue surprise %
- Persist to `pead_earnings_db`, publish `EarningsAnnouncementEvent`
- Scheduled: 6:00 AM ET (pre-market) and 5:00 PM ET (post-market) scrapers
- Historical backfill endpoint for backtesting data population

Key classes:
```
EarningsIngestionApplication
scheduler/EarningsIngestionScheduler     ← @Scheduled triggers
scheduler/HistoricalBackfillScheduler
client/PolygonEarningsClient             ← WebClient + Resilience4j circuit breaker
client/FmpEarningsClient
service/EarningsIngestionService
service/EarningsSurpriseCalculator       ← (actual - estimate) / |estimate| * 100
service/EarningsNormalizationService     ← normalizes across data sources
kafka/EarningsEventPublisher             ← outbox pattern
kafka/OutboxPublisher                    ← @Scheduled, polls outbox_events table
```

### pead-market-data (Phase 1)

Responsibilities:
- EOD OHLCV ingestion from Polygon.io REST
- WebSocket streaming for real-time price bars
- Compute EMA(20), EMA(50), ATR(14), relative volume
- Detect gaps: `(open - prevClose) / prevClose > threshold`
- Publish `PriceBarEvent`, `GapDetectedEvent`

Key classes:
```
MarketDataApplication
client/PolygonRestClient
client/PolygonWebSocketClient
service/TechnicalIndicatorService        ← EMA: EMA_t = price * k + EMA_{t-1} * (1-k), k = 2/(n+1)
service/GapDetectionService
service/RelativeVolumeService
kafka/PriceBarEventPublisher
kafka/GapEventPublisher
stream/EmaCalculatorStream               ← Kafka Streams for streaming EMA computation
```

### pead-scanner (Phase 1 — Kafka Streams, stateless)

Responsibilities:
- Join `EarningsAnnouncementEvent` + `GapDetectedEvent` within 24-hour window
- Apply pre-filter: EPS beat/miss, gap > 5%, relative volume > 2x
- Publish `CandidateSymbolEvent`
- Maintain Redis watchlist of active candidates

**Kafka Streams topology:**
```
earningsStream (pead.earnings.announcement.v1)
    .join(gapStream, candidateBuilder, JoinWindows.ofTimeDifference(24h))
    .filter(meetsPreFilterCriteria)
    .to("pead.scanner.candidate.v1")
```

### pead-strategy-validator (Phase 1)

Responsibilities:
- Consume `CandidateSymbolEvent`
- Apply full PEAD rules (all long/short conditions including EMA alignment, close-near-high)
- Calculate PEAD Score (0-100, threshold 60 to pass)
- Calculate trade setup: entry = Day+2 breakout above earnings candle high, stop = earnings candle low, target1 = 2R, target2 = 3R
- Publish `ValidatedSignalEvent` or `RejectedCandidateEvent`

**PEAD Scoring (100 points total):**

| Component | Weight | Input |
|---|---|---|
| EPS Surprise Magnitude | 25 pts | epsSurprisePct |
| Revenue Surprise Magnitude | 20 pts | revenueSurprisePct |
| Gap Strength | 20 pts | gapPct |
| Relative Volume | 15 pts | relVolume |
| Trend Alignment (EMA20 + EMA50) | 10 pts | aboveEma20 AND aboveEma50 |
| Close Position (close > 95% of high) | 10 pts | closeNearHigh |

Signals with score < 60 → `RejectedCandidateEvent`.

Key scoring classes: `EpsSurpriseScorer`, `RevenueSurpriseScorer`, `GapStrengthScorer`, `VolumeScorer`, `TrendScorer`, `ClosePositionScorer`

### pead-signal-engine (Phase 2)

Responsibilities:
- Consume `ValidatedSignalEvent`, store in Redis (TTL = 2 days)
- Kafka Streams: for each `PriceBarEvent`, check if price crosses above earnings candle high
- Emit `EntrySignalEvent` when breakout detected
- Expire signals on Day+2 EOD

**Key Kafka Streams pattern:**
```
EntryTriggerProcessor (implements Processor<String, PriceBarEvent>)
  → reads from "pending-signals-store" (state store)
  → if currentPrice >= signal.earningsCandleHigh → emit EntrySignalEvent
  → Punctuator runs hourly to clean expired signals
```

### pead-paper-trading (Phase 2)

Responsibilities:
- Consume `EntrySignalEvent` (after risk approval)
- Execute simulated trades with configurable slippage + commission
- Monitor open positions for stop loss / target hits every minute during market hours
- Publish `TradeExecutedEvent`, `PositionClosedEvent`

**State machine:** `SIGNAL_RECEIVED → POSITION_SIZING → ENTRY_PENDING → POSITION_OPEN → [STOP_HIT | TARGET_1_HIT | TARGET_2_HIT | TRAILING_STOP] → POSITION_CLOSED`

**Position sizing formula:**
```java
int shares = (int) Math.floor((accountSize * riskPct) / Math.abs(entryPrice - stopLoss));
// riskPct = 0.01 (1% account risk)
```

**Slippage config (application.yml):**
```yaml
paper.trading:
  slippage-model: FIXED_PCT
  slippage-pct: 0.001       # 0.1%
  commission-per-share: 0.005
  min-commission: 1.0
```

### pead-risk-engine (Phase 3)

Risk rules (all configurable via StrategyConfig):
1. Max 1% account risk per trade
2. Max 5 concurrent open positions
3. Max 20% portfolio in any single sector
4. Daily loss limit: if daily P&L < -2% → halt new entries
5. Max drawdown from peak > 10% → halt new entries
6. Max single position: 10% of portfolio
7. Minimum PEAD score: 60

Flow: `EntrySignalEvent` → all 7 rules checked → PASS: forward to `pead.signal.approved-entry.v1` → FAIL: publish `RiskVetoEvent`

### pead-backtesting (Phase 3)

Design: in-memory event-driven simulation, NOT via Kafka (avoids replay complexity).
- Loads historical `earnings_announcements`, `price_bars`, `daily_indicators` from DB
- Sorts all events by timestamp
- Feeds through **same shared logic** from `pead-common`: `PeadStrategyValidator`, `PositionSizingService`, `PaperTradingExecutionService`
- Generates: equity curve, max drawdown, Sharpe ratio, win rate, profit factor
- Triggered via REST API: `POST /backtests` with `BacktestParameters`

```java
record BacktestParameters(
    LocalDate startDate, LocalDate endDate,
    BigDecimal initialCapital,
    double riskPerTradePct,
    double minEpsSurprisePct, double minRevSurprisePct,
    double minGapPct, double minRelativeVolume,
    int minPeadScore,
    String universe    // "SP500", "NASDAQ100", "ALL"
)
```

---

## 7. Database Schema Design

### Databases (1 per service)

`pead_earnings_db`, `pead_market_data_db`, `pead_strategy_db`, `pead_trading_db`, `pead_risk_db`, `pead_backtesting_db`

Created by `infrastructure/postgres/init.sql` (run at Docker Compose startup).

**pead_earnings_db:**
```sql
earnings_announcements (id, ticker, announcement_date, announcement_time, fiscal_quarter,
    eps_actual, eps_estimate, eps_surprise_pct, revenue_actual, revenue_estimate,
    revenue_surprise_pct, eps_beat, revenue_beat, both_beat, source, raw_payload JSONB,
    UNIQUE(ticker, announcement_date, fiscal_quarter))

outbox_events (id, aggregate_type, aggregate_id, event_type, topic, payload BYTEA,
    partition_key, status, created_at, published_at, retry_count, error_message)
```

**pead_market_data_db:**
```sql
price_bars (ticker, bar_date, timeframe, open_price, high_price, low_price, close_price,
    volume, vwap — partitioned by bar_date)

daily_indicators (ticker, indicator_date, ema_20, ema_50, sma_200, atr_14,
    rel_volume, avg_volume_20d, close_price, pct_from_high, above_ema20, above_ema50)

gap_events (ticker, gap_date, prev_close, open_price, gap_pct, gap_direction,
    rel_volume, earnings_related)
```

**pead_strategy_db:**
```sql
validated_signals (signal_id UUID, ticker, direction, signal_date, earnings_date,
    pead_score, entry_price, stop_loss, target_1, target_2, risk_reward_ratio,
    eps_surprise_pct, revenue_surprise_pct, gap_pct, rel_volume, ema_20, ema_50,
    above_ema20, above_ema50, close_near_high, status, expiry_date, correlation_id)

scoring_details (signal_id, component, raw_value, score, weight, weighted_score)

rejected_candidates (ticker, scan_date, rejection_reasons JSONB, raw_metrics JSONB)
```

**pead_trading_db:**
```sql
portfolios (portfolio_id UUID, name, portfolio_type, initial_capital, current_cash, total_equity)

positions (position_id UUID, portfolio_id, signal_id, ticker, direction, quantity,
    entry_price, current_price, stop_loss, target_1, target_2,
    status, entry_date, exit_date, unrealized_pnl, realized_pnl)

trades (trade_id UUID, position_id, portfolio_id, signal_id, ticker, side, quantity,
    execution_price, slippage, commission, trade_type, executed_at)

portfolio_snapshots (portfolio_id, snapshot_date, total_equity, cash,
    open_positions_value, daily_pnl, cumulative_pnl, drawdown_pct)
```

All schema changes managed via **Flyway** migrations in each service's `src/main/resources/db/migration/`.

### Redis Key Patterns

```
scanner:watchlist                     → SET of active candidate tickers
scanner:candidate:{ticker}            → HASH (TTL 86400s)
signal:pending:{ticker}               → HASH (TTL 172800s)
paper:portfolio:{portfolio_id}        → HASH
paper:position:{position_id}          → HASH
market:price:{ticker}:latest          → HASH (TTL 60s)
market:indicator:{ticker}:{date}      → HASH
risk:exposure:{portfolio_id}          → HASH
risk:daily_pnl:{portfolio_id}:{date}  → STRING
```

---

## 8. Exactly-Once Processing + DLQ Strategy

### Transactional Outbox Pattern

All services that write to DB AND publish to Kafka use outbox pattern to avoid dual-write:
1. Business logic writes domain table + `outbox_events` in one DB transaction
2. `OutboxPublisher` (`@Scheduled`, every 500ms) reads PENDING outbox events, publishes to Kafka
3. Marks `status=PUBLISHED` after Kafka ack

### Producer Config
```yaml
acks: all
enable.idempotence: true
max.in.flight.requests.per.connection: 5
retries: 3
```

### Consumer Config
```yaml
enable-auto-commit: false
isolation.level: read_committed
```
Manual commit via `AckMode.MANUAL_IMMEDIATE` — offset committed only after successful DB write.

### Retry + DLQ

Using Spring Kafka `@RetryableTopic`:
```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltTopicSuffix = ".dlq"
)
```
Auto-creates: `{topic}.retry-0`, `{topic}.retry-1`, `{topic}.retry-2`, `{topic}.dlq`

DLQ processor: Spring Batch job reads DLQ topics daily, logs to Loki, alerts Slack, provides manual replay endpoint.

---

## 9. Spring Boot Module Structure (Identical Per Service)

```
pead-{service-name}/src/main/java/org/pead/{servicename}/
  {ServiceName}Application.java
  config/
    KafkaProducerConfig.java
    KafkaConsumerConfig.java  (or KafkaStreamsConfig.java)
    DatabaseConfig.java
    RedisConfig.java
    ObservabilityConfig.java
  domain/                     ← JPA entities
  repository/                 ← Spring Data JPA
  service/                    ← business logic (no Spring annotations on core logic)
  kafka/
    consumer/
    producer/
  stream/                     ← Kafka Streams topologies
  api/
    controller/
    dto/
    mapper/                   ← MapStruct
  exception/

src/main/resources/
  application.yml
  application-local.yml
  application-prod.yml
  db/migration/
    V1__initial_schema.sql
```

---

## 10. Docker Compose (Local Development)

`docker-compose.yml` — infrastructure services:
- `confluentinc/cp-kafka:7.7.0` in KRaft mode (port 9092)
- `provectuslabs/kafka-ui:latest` (port 8090) — topic/message browser
- `postgres:16-alpine` (port 5432) — single instance, multiple databases
- `redis:7.2-alpine` (port 6379)
- `prom/prometheus:latest` (port 9090)
- `grafana/grafana:latest` (port 3000)

`KAFKA_AUTO_CREATE_TOPICS_ENABLE: false` — topics created only via `scripts/init-topics.sh`.

---

## 11. Observability

### Micrometer + Prometheus

Each service exposes `/actuator/prometheus`. Key custom metrics:

- `pead_earnings_ingestion_runs_total{status}` — ingestion health
- `pead_gap_events_detected_total{direction}` — market data health
- `pead_signals_validated_total{direction}` — strategy quality
- `pead_pead_score_histogram` — score distribution (bucket: 0,20,40,60,80,100)
- `pead_paper_trades_executed_total` — trading activity
- `pead_paper_portfolio_equity_gauge{portfolio_id}` — P&L tracking
- `pead_risk_vetoes_total{rule}` — risk engine activity

### Distributed Tracing

`correlationId` propagated as both Kafka header and OpenTelemetry trace context.

Dependencies per service:
```xml
micrometer-registry-prometheus
micrometer-tracing-bridge-otel
opentelemetry-exporter-zipkin
```

### Grafana Dashboards
1. **PEAD Pipeline Health** — Kafka consumer lag, event throughput, error rates
2. **Trading Performance** — equity curve, daily P&L, win rate, Sharpe, max drawdown
3. **Signal Quality** — signals/day, PEAD score distribution, validation pass rate
4. **Infrastructure** — JVM heap, DB pool saturation, Redis memory, Kafka broker metrics

---

## 12. Strategy Configuration

`StrategyConfig.java` in `pead-common` — `@ConfigurationProperties(prefix="pead.strategy")` Java record:

```java
record StrategyConfig(
    double minEpsSurprisePct,       // default 3.0
    double minRevenueSurprisePct,   // default 2.0
    double minGapPct,               // default 5.0
    double minRelativeVolume,       // default 2.0
    int minPeadScore,               // default 60
    double maxRiskPctPerTrade,      // default 0.01 (1%)
    int maxConcurrentPositions,     // default 5
    double dailyLossLimitPct,       // default 0.02 (2%)
    double maxDrawdownPct,          // default 0.10 (10%)
    double profitTarget1R,          // default 2.0
    double profitTarget2R,          // default 3.0
    boolean enableShortSelling,     // default false (Phase 1)
    List<String> excludedSectors
)
```

K8s ConfigMap `pead-strategy-config` maps these values as environment variables. Hot-reload via `/actuator/refresh` + `@RefreshScope`.

---

## 13. CI/CD Pipeline (GitHub Actions)

`.github/workflows/ci.yml` (per PR):
1. `mvn clean verify` — unit tests
2. `mvn verify -P integration-test` — Testcontainers (PostgreSQL, Kafka, Redis)
3. SonarQube scan + OWASP dependency check
4. `mvn jib:build` — builds Docker image (base: `eclipse-temurin:21-jre-alpine`)

`.github/workflows/deploy-staging.yml` (on main merge):
1. Run CI
2. `helm upgrade --install` to staging namespace
3. Smoke tests (health checks + consumer lag check)

**Jib config per service (pom.xml):**
```xml
<jvmFlags>
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
</jvmFlags>
```

---

## 14. Implementation Order (Phase 1)

Execute in this exact order:

### Step 1: Project Scaffolding
- Replace `pom.xml` with parent reactor POM (Java 21, all dependency management)
- Create `pead-common/pom.xml` + `TopicNames.java`, `ConsumerGroupIds.java`, `StrategyConfig.java`
- Create `pead-common` event classes: all Kafka event POJOs in `org.pead.common.event`
- Run `mvn clean install -pl pead-common` to verify compilation
- Create `docker-compose.yml` + `infrastructure/postgres/init.sql` (creates all 6 databases)
- Create `scripts/init-topics.sh` (create all 16 topics with correct partitions)

### Step 2: pead-earnings-ingestion
- Spring Boot app + PostgreSQL + Kafka producer + Flyway
- `V1__initial_schema.sql`: `earnings_announcements`, `outbox_events`
- `PolygonEarningsClient` (WebClient + circuit breaker)
- `EarningsSurpriseCalculator`
- `EarningsEventPublisher` (outbox pattern)
- `OutboxPublisher` (`@Scheduled(fixedDelay=500)`)
- Integration test: Testcontainers PostgreSQL + Kafka

### Step 3: pead-market-data
- Spring Boot app + PostgreSQL + Kafka producer + Flyway
- `V1__initial_schema.sql`: `price_bars`, `daily_indicators`, `gap_events`
- `PolygonRestClient` (EOD data)
- `TechnicalIndicatorService` (EMA calculation)
- `GapDetectionService`
- Publish `GapDetectedEvent`, `PriceBarEvent`

### Step 4: pead-scanner
- Kafka Streams application (no DB)
- `EarningsGapJoinTopology` — KStream-KStream join with 24h window
- `ScanCriteriaEvaluator`
- Redis watchlist
- Unit test via `TopologyTestDriver`

### Step 5: pead-strategy-validator
- Spring Boot app + PostgreSQL + Kafka consumer + producer + Flyway
- `V1__initial_schema.sql`: `validated_signals`, `scoring_details`, `rejected_candidates`
- All 6 scoring components
- `PeadStrategyValidator` (LONG and SHORT condition evaluation)
- `TradeSetupCalculator` (entry, stop, targets)
- Integration test end-to-end: earnings event → gap event → candidate → validated signal

---

## 15. External Data Sources

Phase 1 uses:
- **Polygon.io** — primary: earnings calendar, OHLCV bars, WebSocket real-time
- **Financial Modeling Prep (FMP)** — secondary/fallback: earnings estimates
- **Alpha Vantage** — tertiary fallback

API clients use circuit breaker (Resilience4j) + retry with exponential backoff. Multiple source support enables cross-validation and failover.

---

## 16. Critical Files

| File | Priority | Why |
|---|---|---|
| `pom.xml` (parent) | P0 | Entire project foundation |
| `pead-common/.../event/*.java` | P0 | All inter-service event contracts |
| `pead-common/.../kafka/TopicNames.java` | P0 | Single source of truth for topic names |
| `docker-compose.yml` | P0 | Local dev infrastructure |
| `infrastructure/postgres/init.sql` | P0 | Creates all databases at startup |
| `scripts/init-topics.sh` | P0 | Creates Kafka topics (auto-create disabled) |
| `pead-earnings-ingestion/.../EarningsIngestionApplication.java` | P1 | First runnable service, validates whole stack |

---

## 17. Verification Plan

After Phase 1 implementation:

1. **Infrastructure:** `docker-compose up -d` → all containers healthy
2. **Topics:** `./scripts/init-topics.sh` → verify with Kafka UI at `localhost:8090`
3. **Earnings flow:** Trigger `EarningsIngestionScheduler` manually via actuator endpoint → verify:
   - Row inserted in `earnings_announcements`
   - Row in `outbox_events` with `status=PENDING` → after `OutboxPublisher` runs → `status=PUBLISHED`
   - Message visible in Kafka UI on `pead.earnings.announcement.v1`
4. **Market data flow:** Trigger backfill → verify `price_bars`, `daily_indicators`, `gap_events` populated
5. **Scanner:** Start scanner service → check `pead.scanner.candidate.v1` topic receives messages
6. **Strategy validator:** Verify `validated_signals` table populated, `pead.strategy.validated-signal.v1` has messages
7. **End-to-end trace:** Use `correlationId` from an earnings event and trace it through all topics in Kafka UI
