#!/bin/bash
# PEAD End-to-End Backtest Integration Test
# Prerequisites: docker-compose up -d && all services running
#
# Usage: ./scripts/run-e2e-backtest.sh

set -e

BASE_MARKET=http://localhost:8082
BASE_EARNINGS=http://localhost:8081
BASE_BACKTEST=http://localhost:8088

echo "=== PEAD E2E Backtest Integration Test ==="
echo ""

# Step 1: Verify services are up
echo "[1/5] Checking service health..."
curl -sf $BASE_MARKET/actuator/health > /dev/null || { echo "FAIL: Market Data service not running"; exit 1; }
curl -sf $BASE_EARNINGS/actuator/health > /dev/null || { echo "FAIL: Earnings service not running"; exit 1; }
curl -sf $BASE_BACKTEST/actuator/health > /dev/null || { echo "FAIL: Backtesting service not running"; exit 1; }
echo "  OK: All services healthy"

# Step 2: Backfill price data for RELIANCE (2022-2025)
echo ""
echo "[2/5] Backfilling RELIANCE price data (2022-01-01 to 2025-01-01)..."
BACKFILL_RESULT=$(curl -sf -X POST "$BASE_MARKET/api/v1/market/backfill?ticker=RELIANCE&from=2022-01-01&to=2025-01-01")
echo "  OK: Backfill response: $BACKFILL_RESULT"

# Step 3: Import earnings CSV
echo ""
echo "[3/5] Importing RELIANCE earnings data..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CSV_PATH="$SCRIPT_DIR/../src/test/resources/test-earnings-reliance.csv"
if [ ! -f "$CSV_PATH" ]; then
  echo "  WARN: CSV not found at $CSV_PATH, using inline data..."
  CSV_PATH="/tmp/test-earnings.csv"
  cat > "$CSV_PATH" << 'CSVEOF'
ticker,announcement_date,fiscal_quarter,eps_actual,eps_estimate,revenue_actual,revenue_estimate
RELIANCE,2024-01-19,Q3 FY24,17.56,16.20,2325400000000,2280000000000
RELIANCE,2023-10-20,Q2 FY24,16.32,15.80,2340000000000,2300000000000
RELIANCE,2023-07-21,Q1 FY24,15.85,14.90,2178000000000,2150000000000
RELIANCE,2023-04-21,Q4 FY23,14.18,13.50,2112000000000,2100000000000
RELIANCE,2023-01-20,Q3 FY23,12.80,12.10,2210000000000,2190000000000
RELIANCE,2022-10-21,Q2 FY23,11.56,11.00,2150000000000,2100000000000
RELIANCE,2022-07-22,Q1 FY23,10.94,10.50,2175000000000,2130000000000
RELIANCE,2022-04-22,Q4 FY22,9.82,9.60,1960000000000,1920000000000
CSVEOF
fi

IMPORT_RESULT=$(curl -sf -X POST "$BASE_EARNINGS/api/v1/earnings/import" \
  -F "file=@$CSV_PATH")
echo "  OK: Import result: $IMPORT_RESULT"

# Step 4: Run backtest
echo ""
echo "[4/5] Running 3-year backtest for RELIANCE..."
BACKTEST_RESPONSE=$(curl -sf -X POST "$BASE_BACKTEST/api/v1/backtests" \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2022-01-01",
    "endDate": "2025-01-01",
    "initialCapital": 1000000,
    "universeId": "nifty50",
    "exchange": "NSE"
  }')
BACKTEST_ID=$(echo "$BACKTEST_RESPONSE" | grep -o '"backtestId":"[^"]*"' | cut -d'"' -f4)
echo "  OK: Backtest started: $BACKTEST_ID"

# Step 5: Poll for completion
echo ""
echo "[5/5] Waiting for backtest to complete..."
for i in $(seq 1 30); do
  sleep 2
  STATUS_RESPONSE=$(curl -sf "$BASE_BACKTEST/api/v1/backtests/$BACKTEST_ID")
  STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

  if [ "$STATUS" = "COMPLETED" ]; then
    echo "  OK: Backtest completed!"
    echo ""
    echo "=== RESULTS ==="
    echo "$STATUS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$STATUS_RESPONSE"
    echo ""

    # Fetch trades
    TRADES=$(curl -sf "$BASE_BACKTEST/api/v1/backtests/$BACKTEST_ID/trades")
    TRADE_COUNT=$(echo "$TRADES" | grep -o '"id"' | wc -l)
    echo "Total trades: $TRADE_COUNT"

    echo ""
    echo "=== E2E TEST PASSED ==="
    exit 0
  elif [ "$STATUS" = "FAILED" ]; then
    echo "  FAIL: Backtest failed!"
    echo "$STATUS_RESPONSE"
    exit 1
  fi

  echo "  ... status: $STATUS (attempt $i/30)"
done

echo "  FAIL: Backtest timed out after 60 seconds"
exit 1
