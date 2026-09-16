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
        VelumTunnel.up(context, prefs)
        val valid = verify(context, maxWaitMs, cancelled)
        if (!valid && !cancelled()) runCatching { VelumTunnel.down(context) }
        return valid
    }

    /** Satu primitive restart endpoint: transisi atomik lalu handshake wajib. */
    fun reconnect(
        context: Context,
        prefs: Prefs,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        if (cancelled()) return false
        VelumTunnel.restart(context, prefs, shouldContinue = { !cancelled() })
        val valid = verify(context, maxWaitMs, cancelled)
        if (!valid && !cancelled()) runCatching { VelumTunnel.down(context) }
        return valid
    }

    /** Memverifikasi tunnel yang sudah dibangun, termasuk handshake WireGuard. */
    fun verify(
        context: Context,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        val handshake = awaitHandshake(context, maxWaitMs, cancelled)
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
        cancelled: () -> Boolean = { false }
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cancelled()) return false
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if ((VelumTunnel.traffic(context)?.latestHandshakeMs ?: 0L) > 0L) return true
            try {
                Thread.sleep(HANDSHAKE_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }
}
