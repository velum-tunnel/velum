package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Test

class VelumLinkHealthTest {
    @Test
    fun tunnelDownSelaluOffline() {
        assertEquals(
            VelumLinkHealth.OFFLINE,
            VelumLinkHealthDecision.decide(false, true, 1_000L, 2_000L)
        )
    }

    @Test
    fun statistikTidakTerbacaDegraded() {
        assertEquals(
            VelumLinkHealth.DEGRADED,
            VelumLinkHealthDecision.decide(true, false, 1_000L, 2_000L)
        )
    }

    @Test
    fun handshakeStaleBertahap() {
        val now = 1_000_000L
        assertEquals(
            VelumLinkHealth.CONNECTED,
            VelumLinkHealthDecision.decide(true, true, now - 1_000L, now)
        )
        assertEquals(
            VelumLinkHealth.DEGRADED,
            VelumLinkHealthDecision.decide(
                true, true, now - VelumLinkHealthDecision.DEGRADED_AFTER_MS, now
            )
        )
        assertEquals(
            VelumLinkHealth.OFFLINE,
            VelumLinkHealthDecision.decide(
                true, true, now - VelumLinkHealthDecision.OFFLINE_AFTER_MS, now
            )
        )
    }
}
