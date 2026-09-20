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
    ): Boolean = establishAndVerify(context, prefs, maxWaitMs, cancelled) {
        VelumTunnel.up(context, prefs)
    }

    /** Satu primitive restart endpoint: transisi atomik lalu handshake wajib. */
    fun reconnect(
        context: Context,
        prefs: Prefs,
        maxWaitMs: Long,
        cancelled: () -> Boolean = { false }
    ): Boolean = establishAndVerify(context, prefs, maxWaitMs, cancelled) {
        VelumTunnel.restart(context, prefs, shouldContinue = { !cancelled() })
    }

    /**
     * Menjalankan transisi tunnel lalu memverifikasi handshake baru.
     *
     * Baseline handshake dan cleanup sengaja berada di satu tempat: `connect` dan
     * `reconnect` sebelumnya memiliki dua salinan yang mudah tidak sinkron ketika
     * aturan pembatalan atau teardown berubah.
     */
    private fun establishAndVerify(
        context: Context,
        prefs: Prefs,
        maxWaitMs: Long,
        cancelled: () -> Boolean,
        establish: () -> Unit
    ): Boolean {
        if (cancelled()) return false
        val baseline = VelumTunnel.traffic(context)?.latestHandshakeMs ?: 0L
        establish()
        val valid = verify(context, maxWaitMs, baseline, cancelled)
        cleanupIfStillOwned(context, valid, cancelled)
        return valid
    }

    /** Memverifikasi tunnel yang sudah dibangun, termasuk handshake WireGuard. */
    fun verify(
        context: Context,
        maxWaitMs: Long,
        baselineHandshakeMs: Long = 0L,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        val handshake = awaitHandshake(context, maxWaitMs, baselineHandshakeMs, cancelled)
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
        baselineHandshakeMs: Long = 0L,
        cancelled: () -> Boolean = { false }
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (cancelled()) return false
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if (isFreshHandshake(
                    VelumTunnel.traffic(context)?.latestHandshakeMs ?: 0L,
                    baselineHandshakeMs
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

    /** Timestamp lama tidak boleh mengesahkan endpoint/sesi yang baru dibangun. */
    fun isFreshHandshake(latestHandshakeMs: Long, baselineHandshakeMs: Long): Boolean =
        latestHandshakeMs > 0L && latestHandshakeMs > baselineHandshakeMs

    private fun cleanupIfStillOwned(context: Context, valid: Boolean, cancelled: () -> Boolean) {
        if (valid) return
        // Cancellation bisa berarti intent baru sudah mengambil alih. Beri pemilik baru
        // kesempatan menyelesaikan transisinya, lalu teardown hanya bila operasi lama masih
        // melihat dirinya tidak dibatalkan. Caller intent baru bertanggung jawab atas state.
        if (!cancelled()) runCatching { VelumTunnel.down(context) }
    }
}
