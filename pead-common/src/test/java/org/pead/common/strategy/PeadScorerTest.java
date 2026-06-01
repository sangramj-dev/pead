package org.pead.common.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pead.common.strategy.scoring.PeadScoreResult;

import static org.junit.jupiter.api.Assertions.*;

class PeadScorerTest {

    private PeadScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new PeadScorer();
    }

    /**
     * Strong beat scenario:
     *   eps=16%       -> 21 (>= 10)
     *   rev=7.8%      -> 17 (>= 5)
     *   gap=10%       -> 14 (>= 8)
     *   vol=3.1x      -> 10 (>= 3)
     *   both EMAs     -> 10
     *   close=7850, high=7950 -> 7850/7950 = 98.7% -> 10
     *   total = 21+17+14+10+10+10 = 82
     */
    @Test
    void strongBeatScoresAbove80() {
        PeadScoreResult result = scorer.score(16.0, 7.8, 10.0, 3.1, true, true, 7850.0, 7950.0);
        assertTrue(result.totalScore() >= 80,
                "Expected score >= 80 but got " + result.totalScore());
    }

    /**
     * Weak beat scenario:
     *   eps=2%   -> 0 (< 3)
     *   rev=1%   -> 0 (< 2)
     *   gap=3%   -> 0 (< 5)
     *   vol=1.5x -> 0 (< 2)
     *   no EMAs  -> 0
     *   close=100, high=120 -> 83.3% -> 0
     *   total = 0 -> fails passes(60)
     */
    @Test
    void weakBeatScoresBelowThreshold() {
        PeadScoreResult result = scorer.score(2.0, 1.0, 3.0, 1.5, false, false, 100.0, 120.0);
        assertFalse(result.passes(60),
                "Expected score to fail passes(60) but totalScore was " + result.totalScore());
    }

    /**
     * Maximum score scenario:
     *   eps >= 20  -> 25
     *   rev >= 10  -> 20
     *   gap >= 20  -> 20
     *   vol >= 10  -> 15
     *   both EMAs  -> 10
     *   close == high (100%) -> 10
     *   total = 100
     */
    @Test
    void maxScoreIs100() {
        PeadScoreResult result = scorer.score(25.0, 15.0, 25.0, 12.0, true, true, 1000.0, 1000.0);
        assertEquals(100, result.totalScore(),
                "Expected maximum score of 100 but got " + result.totalScore());
    }
}
