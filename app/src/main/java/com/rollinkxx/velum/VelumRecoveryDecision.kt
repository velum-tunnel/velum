package com.rollinkxx.velum

/**
 * Keputusan murni untuk pemicu recovery otomatis.
 *
 * Memisahkan guard niat dari Android callback agar aturan yang mencegah tunnel hidup
 * kembali setelah Putuskan dapat diuji tanpa perangkat.
 */
object VelumRecoveryDecision {
    enum class Trigger {
        BOOT_OR_UPDATE,
        TUNNEL_DOWN,
        NETWORK
    }

    fun shouldSchedule(
        trigger: Trigger,
        wasUp: Boolean,
        registered: Boolean,
        tunnelUp: Boolean,
        bouncing: Boolean,
        intentStale: Boolean
    ): Boolean {
        if (!wasUp || !registered || bouncing || intentStale) return false
        if (trigger == Trigger.TUNNEL_DOWN || trigger == Trigger.BOOT_OR_UPDATE) {
            return !tunnelUp
        }
        return true
    }
}

fun VelumRecoveryDecision.Trigger.requiresTunnelDown(): Boolean =
    this == VelumRecoveryDecision.Trigger.TUNNEL_DOWN ||
        this == VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE
