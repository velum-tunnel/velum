package com.rollinkxx.velum

/** Owns one process-wide callback without allowing an older owner to clear a newer one. */
internal class VelumListenerSlot<T> {
    @Volatile
    var current: T? = null
        set(value) {
            synchronized(this) {
                field = value
            }
        }

    fun clearIfCurrent(owner: T): Boolean {
        synchronized(this) {
            if (current !== owner) return false
            current = null
            return true
        }
    }
}
