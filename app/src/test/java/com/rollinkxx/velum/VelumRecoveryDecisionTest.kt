package com.rollinkxx.velum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumRecoveryDecisionTest {
    private fun should(
        trigger: VelumRecoveryDecision.Trigger,
        wasUp: Boolean = true,
        registered: Boolean = true,
        tunnelUp: Boolean = false,
        bouncing: Boolean = false,
        intentStale: Boolean = false
    ) = VelumRecoveryDecision.shouldSchedule(
        trigger, wasUp, registered, tunnelUp, bouncing, intentStale
    )

    @Test
    fun swipeTidakMengubahNiat() {
        // Swipe hanya menghancurkan Activity; tanpa event disconnect, guard recovery tetap sah.
        assertTrue(should(VelumRecoveryDecision.Trigger.TUNNEL_DOWN))
    }

    @Test
    fun tombolPutuskanMembatalkanRecovery() {
        assertFalse(should(VelumRecoveryDecision.Trigger.TUNNEL_DOWN, wasUp = false))
    }

    @Test
    fun bootDenganNetworkSudahTersediaTetapMenjadwalkanRecovery() {
        assertTrue(should(VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE))
    }

    @Test
    fun tunnelDownTanpaPerubahanNetworkMenjadwalkanRecovery() {
        assertTrue(should(VelumRecoveryDecision.Trigger.TUNNEL_DOWN))
    }

    @Test
    fun tunnelSudahUpTidakPerluRecoveryBoot() {
        assertFalse(should(VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE, tunnelUp = true))
    }

    @Test
    fun niatBaruMembatalkanRecoveryLama() {
        assertFalse(should(VelumRecoveryDecision.Trigger.TUNNEL_DOWN, intentStale = true))
    }

    @Test
    fun pekerjaanRecoveryYangSedangBerjalanTidakDigandakan() {
        assertFalse(should(VelumRecoveryDecision.Trigger.NETWORK, bouncing = true))
    }

    @Test
    fun belumTerdaftarTidakDipulihkanOtomatis() {
        assertFalse(should(VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE, registered = false))
    }
}
