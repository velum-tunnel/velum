package com.rollinkxx.velum

import android.content.Context
import android.os.SystemClock
import com.wireguard.android.backend.Tunnel

/**
 * Satu jalur bukti untuk semua pelaku koneksi: State.UP saja tidak cukup; harus ada
 * handshake WireGuard dan niat belum digantikan.
 */
object VelumConnectionContract {
    const val HANDSHAKE_WAIT_MS = 8_000L
    const val HANDSHAKE_POLL_MS = 250L

    fun accepted(tunnelUp: Boolean, handshakeReady: Boolean, intentStale: Boolean): Boolean =
        tunnelUp && handshakeReady && !intentStale

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
