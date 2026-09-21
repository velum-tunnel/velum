package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumStateGuardsTest {
    @Test
    fun downWhenAlreadyDownDoesNotCreateExpectedToken() {
        val transitions = ExpectedDownTransitions()

        transitions.expectIfUp(isUp = false)

        assertFalse(transitions.consume())
        assertEquals(0, transitions.pendingCount())
    }

    @Test
    fun expectedDownTokenIsConsumedExactlyOnce() {
        val transitions = ExpectedDownTransitions()

        transitions.expectIfUp(isUp = true)

        assertTrue(transitions.consume())
        assertFalse(transitions.consume())
        assertEquals(0, transitions.pendingCount())
    }

    @Test
    fun cancellingFailedDownDoesNotLeaveTokenBehind() {
        val transitions = ExpectedDownTransitions()

        transitions.expectIfUp(isUp = true)
        transitions.cancel()

        assertFalse(transitions.consume())
        assertEquals(0, transitions.pendingCount())
    }

    @Test
    fun trafficResumptionIsDetectedWhenEitherCounterChanges() {
        assertTrue(VelumTrafficDecision.trafficResumed(11, 10, 20, 20))
        assertTrue(VelumTrafficDecision.trafficResumed(10, 10, 21, 20))
        assertFalse(VelumTrafficDecision.trafficResumed(10, 10, 20, 20))
    }
}
