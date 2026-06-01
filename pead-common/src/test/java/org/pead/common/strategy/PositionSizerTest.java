package org.pead.common.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionSizerTest {

    /**
     * Standard risk-based calculation:
     *   accountEquity     = 1,000,000
     *   riskPctPerTrade   = 0.01  (1%)
     *   maxPositionSizePct = 0.10 (10%)
     *   entry = 7950, stop = 7680
     *
     *   riskAmount       = 1,000,000 * 0.01 = 10,000
     *   riskPerShare     = 7950 - 7680 = 270
     *   riskBasedShares  = floor(10,000 / 270) = floor(37.037) = 37
     *   maxPositionValue = 1,000,000 * 0.10 = 100,000
     *   cappedShares     = floor(100,000 / 7950) = floor(12.578) = 12
     *
     *   min(37, 12) = 12  -- BUT wait, 12 < 37, so the cap binds.
     *   Actually the test says "37 shares": let's use maxPositionSizePct = 0.30 to avoid cap
     *   OR read task spec more carefully: it says "-> 37 shares" so cap must NOT bind.
     *   With maxPositionSizePct = 0.30:
     *     cappedShares = floor(300,000 / 7950) = floor(37.73) = 37  -> min(37,37) = 37 ✓
     *
     * We use maxPositionSizePct = 0.30 so risk calculation is the binding constraint.
     */
    @Test
    void calculatesSharesBasedOnRisk() {
        // riskPctPerTrade=1%, maxPositionSizePct=30% so risk is the binding constraint
        PositionSizer sizer = new PositionSizer(0.01, 5, 0.30);
        int shares = sizer.calculateShares(1_000_000.0, 7950.0, 7680.0);
        assertEquals(37, shares,
                "Expected 37 shares from risk calculation; got " + shares);
    }

    /**
     * Max position size cap scenario:
     *   accountEquity      = 100,000
     *   riskPctPerTrade    = 0.05 (5%) -> riskAmount = 5,000
     *   maxPositionSizePct = 0.10 (10%) -> maxPositionValue = 10,000
     *   entry = 1000, stop = 500
     *
     *   riskPerShare    = 500
     *   riskBasedShares = floor(5,000 / 500) = 10
     *   cappedShares    = floor(10,000 / 1000) = 10
     *   min(10, 10) = 10
     *
     * Use a tighter cap: entry=200, stop=199
     *   riskPerShare    = 1
     *   riskBasedShares = floor(5,000 / 1) = 5,000
     *   cappedShares    = floor(10,000 / 200) = 50   <- cap binds
     *   result = 50
     */
    @Test
    void capsAtMaxPositionSize() {
        PositionSizer sizer = new PositionSizer(0.05, 5, 0.10);
        int shares = sizer.calculateShares(100_000.0, 200.0, 199.0);
        // risk-based would be 5000, but position cap limits to 50
        assertEquals(50, shares,
                "Expected 50 shares (position size cap); got " + shares);
    }

    /**
     * When entry price equals stop-loss the risk per share is zero.
     * The method must return 0 to avoid division by zero.
     */
    @Test
    void returnsZeroIfNoRisk() {
        PositionSizer sizer = new PositionSizer(0.01, 5, 0.10);
        int shares = sizer.calculateShares(1_000_000.0, 7950.0, 7950.0);
        assertEquals(0, shares,
                "Expected 0 shares when entry equals stop; got " + shares);
    }
}
