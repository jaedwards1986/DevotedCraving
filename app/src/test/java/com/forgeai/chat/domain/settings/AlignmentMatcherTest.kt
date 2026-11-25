package com.forgeai.chat.domain.settings

import org.junit.Assert.*
import org.junit.Test

class AlignmentMatcherTest {
    @Test fun exactMatchGetsFullAlignment() {
        val result = AlignmentMatcher().match(72.0, 72.0)
        assertEquals(100.0, result.score, 0.0)
        assertTrue(result.withinTolerance)
    }

    @Test fun differenceInsideToleranceGetsFullAlignment() {
        val result = AlignmentMatcher(tolerance = 15.0).match(85.0, 100.0)
        assertEquals(100.0, result.score, 0.0)
        assertTrue(result.withinTolerance)
    }

    @Test fun scoreFallsLinearlyOutsideTolerance() {
        val result = AlignmentMatcher(tolerance = 10.0).match(40.0, 60.0)
        assertEquals(66.6667, result.score, 0.01)
        assertFalse(result.withinTolerance)
    }

    @Test fun differenceAtFourToleranceWidthsReachesZero() {
        val result = AlignmentMatcher(tolerance = 10.0).match(0.0, 40.0)
        assertEquals(0.0, result.score, 0.0)
    }

    @Test fun hardGateOverridesExactNumericalMatch() {
        val result = AlignmentMatcher().match(80.0, 80.0, hardGateTriggered = true)
        assertEquals(0.0, result.score, 0.0)
        assertTrue(result.hardGateTriggered)
    }

    @Test fun rejectsNonFiniteOrOutOfRangeScores() {
        val matcher = AlignmentMatcher()
        assertThrows(IllegalArgumentException::class.java) { matcher.match(Double.NaN, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { matcher.match(50.0, Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { matcher.match(-1.0, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { matcher.match(50.0, 101.0) }
    }

    @Test fun rejectsInvalidTolerance() {
        assertThrows(IllegalArgumentException::class.java) { AlignmentMatcher(0.0) }
        assertThrows(IllegalArgumentException::class.java) { AlignmentMatcher(101.0) }
    }
}
