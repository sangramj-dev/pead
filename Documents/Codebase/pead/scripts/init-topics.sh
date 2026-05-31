#!/bin/bash
# PEAD Trading Platform - Kafka Topic Initialization
# Run after docker-compose up -d and Kafka is healthy.
# Usage: ./scripts/init-topics.sh [bootstrap-server]
#
# First-time setup: chmod +x scripts/init-topics.sh

BOOTSTRAP=${1:-localhost:9092}
REPLICATION=1   # Use 3 in production

echo "Creating PEAD Kafka topics on ${BOOTSTRAP}..."

# Helper function
create_topic() {
  local topic=$1
  local partitions=$2
  local retention_ms=$3  # -1 = infinite

  echo "  Creating: ${topic} (partitions=${partitions})"
  kafka-topics.sh --create \
    --bootstrap-server "${BOOTSTRAP}" \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor "${REPLICATION}" \
    --if-not-exists \
    --config retention.ms="${retention_ms}" \
    --config cleanup.policy=delete
}

echo ""
echo "=== EARNINGS DOMAIN ==="
create_topic "pead.earnings.announcement.v1"        8   2592000000  # 30 days
create_topic "pead.earnings.estimate.v1"             4   2592000000  # 30 days

echo ""
echo "=== MARKET DATA DOMAIN ==="
create_topic "pead.market.price-bar.v1"             16    604800000  # 7 days
create_topic "pead.market.gap-detected.v1"           8    604800000  # 7 days
create_topic "pead.market.volume-anomaly.v1"         8    604800000  # 7 days

echo ""
echo "=== SCANNER DOMAIN ==="
create_topic "pead.scanner.candidate.v1"             8   1209600000  # 14 days

echo ""
echo "=== STRATEGY DOMAIN ==="
create_topic "pead.strategy.validated-signal.v1"     8   1209600000  # 14 days
create_topic "pead.strategy.rejected-candidate.v1"   4    604800000  # 7 days

echo ""
echo "=== SIGNAL DOMAIN ==="
create_topic "pead.signal.entry-signal.v1"           8    259200000  # 3 days
create_topic "pead.signal.approved-entry.v1"         8    259200000  # 3 days
create_topic "pead.signal.exit-signal.v1"            8    259200000  # 3 days

echo ""
echo "=== RISK DOMAIN ==="
create_topic "pead.risk.veto.v1"                     4    259200000  # 3 days

echo ""
echo "=== TRADING DOMAIN ==="
create_topic "pead.trading.trade-executed.v1"        8   7776000000  # 90 days
create_topic "pead.trading.position-closed.v1"       8   7776000000  # 90 days
create_topic "pead.trading.position-updated.v1"      8    604800000  # 7 days

echo ""
echo "=== DLQ ==="
create_topic "pead.dlq.v1"                           4    604800000  # 7 days

echo ""
echo "=== RETRY / DLT (for @RetryableTopic) ==="
# pead-strategy-validator retries for pead.scanner.candidate.v1
create_topic "pead.scanner.candidate.v1-retry-1000"  8    259200000  # 3 days (1s delay)
create_topic "pead.scanner.candidate.v1-retry-2000"  8    259200000  # 3 days (2s delay)
create_topic "pead.scanner.candidate.v1-retry-4000"  8    259200000  # 3 days (4s delay)
create_topic "pead.scanner.candidate.v1-dlt"         8   7776000000  # 90 days (manual replay)

echo ""
echo "Done! Listing all created topics:"
kafka-topics.sh --list --bootstrap-server "${BOOTSTRAP}" | grep "^pead\."
