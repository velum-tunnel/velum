package com.rollinkxx.velum

/** Kesehatan jalur di atas transport TUN; berbeda dari Tunnel.State.UP. */
enum class VelumLinkHealth {
    CONNECTED,
    DEGRADED,
    OFFLINE
}

object VelumLinkHealthDecision {
    const val DEGRADED_AFTER_MS = 45_000L
    const val OFFLINE_AFTER_MS = 180_000L

    fun decide(
        tunnelUp: Boolean,
        statisticsReadable: Boolean,
        latestHandshakeEpochMs: Long,
        nowEpochMs: Long,
        trafficActive: Boolean = false
    ): VelumLinkHealth {
        if (!tunnelUp) return VelumLinkHealth.OFFLINE
        if (!statisticsReadable) return VelumLinkHealth.DEGRADED
        if (trafficActive) return VelumLinkHealth.CONNECTED
        if (latestHandshakeEpochMs <= 0L) return VelumLinkHealth.DEGRADED
        val age = (nowEpochMs - latestHandshakeEpochMs).coerceAtLeast(0L)
        return when {
            age >= OFFLINE_AFTER_MS -> VelumLinkHealth.OFFLINE
            age >= DEGRADED_AFTER_MS -> VelumLinkHealth.DEGRADED
            else -> VelumLinkHealth.CONNECTED
        }
    }
}

/** Proses-wide snapshot yang dapat dibaca UI, monitor, dan notification. */
object VelumLinkHealthStore {
    @Volatile
    var current: VelumLinkHealth = VelumLinkHealth.OFFLINE
        private set

    fun update(value: VelumLinkHealth) {
        current = value
    }
}
