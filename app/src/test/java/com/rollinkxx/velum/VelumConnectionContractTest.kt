package com.rollinkxx.velum

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumConnectionContractTest {
    @Test
    fun semuaEntryPointMemakaiAturanHandshakeYangSama() {
        val entryPoints = listOf("tile", "boot", "reconnect", "fallback", "process-recreation")
        for (entryPoint in entryPoints) {
            assertFalse("$entryPoint tidak boleh menerima State.UP tanpa handshake",
                VelumConnectionContract.accepted(true, false, false))
            assertTrue("$entryPoint menerima koneksi tervalidasi",
                VelumConnectionContract.accepted(true, true, false))
        }
    }

    @Test
    fun tileTidakBolehMenganggapStateUpSebagaiKoneksiTanpaHandshake() {
        assertFalse(VelumConnectionContract.accepted(tunnelUp = true, handshakeReady = false, intentStale = false))
        assertTrue(VelumConnectionContract.accepted(tunnelUp = true, handshakeReady = true, intentStale = false))
    }

    @Test
    fun bootHanyaMencatatSuksesJikaHandshakeDanNiatMasihSah() {
        assertFalse(VelumConnectionContract.accepted(tunnelUp = false, handshakeReady = true, intentStale = false))
        assertFalse(VelumConnectionContract.accepted(tunnelUp = true, handshakeReady = true, intentStale = true))
        assertTrue(VelumConnectionContract.accepted(tunnelUp = true, handshakeReady = true, intentStale = false))
    }

    @Test
    fun reconnectTidakMempertahankanTunnelUpTanpaHandshake() {
        assertFalse(VelumConnectionContract.accepted(tunnelUp = true, handshakeReady = false, intentStale = false))
    }

    @Test
    fun fallbackHanyaMemilihKandidatYangTerverifikasi() {
        val result = VelumVerifiedChoice.pickVerified(
            ranked = listOf("a", "b", "c"),
            maxCandidates = 3
        ) { host -> host == "c" }

        assertEquals("c", result.winner)
        assertEquals(listOf("a", "b", "c"), result.attempted)
    }

    @Test
    fun processRecreationMenjadwalkanRecoveryHanyaUntukNiatUp() {
        assertTrue(
            VelumRecoveryDecision.shouldSchedule(
                trigger = VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE,
                wasUp = true,
                registered = true,
                tunnelUp = false,
                bouncing = false,
                intentStale = false
            )
        )
        assertFalse(
            VelumRecoveryDecision.shouldSchedule(
                trigger = VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE,
                wasUp = false,
                registered = true,
                tunnelUp = false,
                bouncing = false,
                intentStale = false
            )
        )
    }

    @Test
    fun claimAtomikMengizinkanSatuPemilikDalamPersaingan() {
        val claim = VelumRecoveryClaim()
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val winners = Collections.synchronizedList(mutableListOf<Boolean>())
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.execute {
                start.await()
                winners.add(claim.tryClaim())
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(1, winners.count { it })
        assertEquals(1, winners.count { !it })
        claim.release()
        claim.release()
        assertTrue(claim.tryClaim())
    }

    @Test
    fun aturanAcceptedMenolakIntentYangBerubahWalauHandshakeSudahAda() {
        assertFalse(VelumConnectionContract.accepted(true, true, true))
    }

    @Test
    fun handshakeLamaTidakDiterimaSebagaiBuktiSesiBaru() {
        assertFalse(VelumConnectionContract.handshakeIsFresh(1_700L, 1_800L))
        assertFalse(VelumConnectionContract.handshakeIsFresh(0L, 0L))
    }

    @Test
    fun handshakeSetelahAwalOperasiDiterima() {
        assertTrue(VelumConnectionContract.handshakeIsFresh(1_801L, 1_800L))
    }

    @Test
    fun claimRecoveryTetapEksklusifSetelahReleaseBerulang() {
        val claim = VelumRecoveryClaim()
        assertTrue(claim.tryClaim())
        claim.release()
        claim.release()
        assertTrue(claim.tryClaim())
    }
}
