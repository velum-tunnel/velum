package com.rollinkxx.velum

import android.content.Context
import android.os.SystemClock
import com.wireguard.android.backend.Tunnel

/** Satu jalur bukti untuk semua pelaku koneksi: State.UP saja tidak cukup. */
object VelumConnectionContract {
    const val HANDSHAKE_WAIT_MS = 8_000L
    const val HANDSHAKE_POLL_MS = 250L

    fun accepted(tunnelUp: Boolean, handshakeReady: Boolean, intentStale: Boolean): Boolean =
        tunnelUp && handshakeReady && !intentStale

    /** Satu primitive pembangunan koneksi untuk UI, tile, boot, dan recovery. */
    fun connect(
        context: Context,
        prefs: Prefs,
        maxWaitMs: Long = HANDSHAKE_WAIT_MS,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        if (cancelled()) return false
        val minimumHandshakeEpochMs = System.currentTimeMillis()
        VelumForegroundService.start(context)
        return try {
            VelumTunnel.up(context, prefs)
            val valid = verify(context, maxWaitMs, cancelled, minimumHandshakeEpochMs)
            if (!valid && !cancelled()) rollback(context)
            if (valid) {
                VelumLinkHealthStore.update(VelumLinkHealth.CONNECTED)
                StatusNotifier.show(context)
            }
            valid
        } catch (e: Throwable) {
            if (!cancelled()) rollback(context)
            throw e
        }
    }

    /** Satu primitive restart endpoint: transisi atomik lalu handshake wajib. */
    fun reconnect(
        context: Context,
        prefs: Prefs,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        if (cancelled()) return false
        val minimumHandshakeEpochMs = System.currentTimeMillis()
        VelumForegroundService.start(context)
        return try {
            VelumTunnel.restart(context, prefs, shouldContinue = { !cancelled() })
            val valid = verify(context, maxWaitMs, cancelled, minimumHandshakeEpochMs)
            if (!valid && !cancelled()) rollback(context)
            if (valid) {
                VelumLinkHealthStore.update(VelumLinkHealth.CONNECTED)
                StatusNotifier.show(context)
            }
            valid
        } catch (e: Throwable) {
            if (!cancelled()) rollback(context)
            throw e
        }
    }

    /** Roll back both tunnel and companion service after an unsuccessful attempt. */
    private fun rollback(context: Context) {
        runCatching { VelumTunnel.down(context) }
        VelumForegroundService.stop(context)
        VelumLinkHealthStore.update(VelumLinkHealth.OFFLINE)
    }

    /** Memverifikasi tunnel yang sudah dibangun, termasuk handshake WireGuard. */
    fun verify(
        context: Context,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false },
        minimumHandshakeEpochMs: Long = 0L
    ): Boolean {
        val handshake = awaitHandshake(context, maxWaitMs, cancelled, minimumHandshakeEpochMs)
        return accepted(
            tunnelUp = VelumTunnel.state == Tunnel.State.UP,
            handshakeReady = handshake,
            intentStale = cancelled()
        )
    }

    /** Blocking; panggil hanya dari thread latar. */
    fun awaitHandshake(
        context: Context,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false },
        minimumHandshakeEpochMs: Long = 0L
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cancelled()) return false
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if (handshakeIsFresh(
                    VelumTunnel.traffic(context)?.latestHandshakeMs ?: 0L,
                    minimumHandshakeEpochMs
                )
            ) return true
            try {
                Thread.sleep(HANDSHAKE_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }

    /** WireGuard statistics use Unix epoch milliseconds; a prior session must not qualify. */
    fun handshakeIsFresh(latestHandshakeEpochMs: Long, minimumHandshakeEpochMs: Long): Boolean =
        latestHandshakeEpochMs > 0L && latestHandshakeEpochMs > minimumHandshakeEpochMs
}
